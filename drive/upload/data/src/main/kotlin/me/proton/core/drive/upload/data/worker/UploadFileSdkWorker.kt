/*
 * Copyright (c) 2024 Proton AG.
 * This file is part of Proton Core.
 *
 * Proton Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Core.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.drive.upload.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.data.workmanager.addTags
import me.proton.core.drive.base.domain.api.ProtonApiCode.ALREADY_EXISTS
import me.proton.core.drive.base.domain.api.ProtonApiCode.NOT_EXISTS
import me.proton.core.drive.base.domain.extension.onProtonHttpException
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.usecase.BroadcastMessages
import me.proton.core.drive.linkupload.domain.entity.NetworkTypeProviderType
import me.proton.core.drive.linkupload.domain.entity.UploadFileLink
import me.proton.core.drive.linkupload.domain.usecase.GetUploadFileLink
import me.proton.core.drive.upload.data.extension.getSizeData
import me.proton.core.drive.upload.data.extension.isRetryable
import me.proton.core.drive.upload.data.extension.log
import me.proton.core.drive.upload.data.extension.logTag
import me.proton.core.drive.upload.data.extension.retryOrAbort
import me.proton.core.drive.upload.data.extension.setSize
import me.proton.core.drive.upload.data.extension.uniqueUploadWorkName
import me.proton.core.drive.upload.data.provider.NetworkTypeProvider
import me.proton.core.drive.upload.data.worker.WorkerKeys.KEY_UPLOAD_FILE_LINK_ID
import me.proton.core.drive.upload.data.worker.WorkerKeys.KEY_URI_STRING
import me.proton.core.drive.upload.data.worker.WorkerKeys.KEY_USER_ID
import me.proton.core.drive.upload.domain.exception.UploadNotFoundException
import me.proton.core.drive.upload.domain.manager.UploadErrorManager
import me.proton.core.drive.upload.domain.manager.UploadSdkManager
import me.proton.core.drive.upload.domain.usecase.UploadFileSdk
import me.proton.core.drive.upload.domain.usecase.UploadMetricsNotifier
import me.proton.core.drive.worker.domain.usecase.CanRun
import me.proton.core.drive.worker.domain.usecase.Done
import me.proton.core.drive.worker.domain.usecase.Run
import me.proton.core.util.kotlin.CoreLogger
import me.proton.drive.sdk.UploadAbortedException
import java.util.concurrent.TimeUnit

@HiltWorker
@OptIn(ExperimentalCoroutinesApi::class)
class UploadFileSdkWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    workManager: WorkManager,
    broadcastMessages: BroadcastMessages,
    getUploadFileLink: GetUploadFileLink,
    uploadErrorManager: UploadErrorManager,
    private val uploadFileSdk: UploadFileSdk,
    private val uploadSdkManager: UploadSdkManager,
    private val cleanupWorkers: CleanupWorkers,
    private val networkTypeProviders: @JvmSuppressWildcards Map<NetworkTypeProviderType, NetworkTypeProvider>,
    configurationProvider: ConfigurationProvider,
    uploadMetricsNotifier: UploadMetricsNotifier,
    canRun: CanRun,
    run: Run,
    done: Done,
) : UploadCoroutineWorker(
    appContext = appContext,
    workerParams = workerParams,
    workManager = workManager,
    broadcastMessages = broadcastMessages,
    getUploadFileLink = getUploadFileLink,
    uploadErrorManager = uploadErrorManager,
    configurationProvider = configurationProvider,
    uploadMetricsNotifier = uploadMetricsNotifier,
    canRun = canRun,
    run = run,
    done = done,
) {
    private val uriString = requireNotNull(inputData.getString(KEY_URI_STRING))

    override suspend fun doLimitedRetryUploadWork(uploadFileLink: UploadFileLink): Result {
        uploadFileLink.logWorkState("Uploading via SDK (size = ${uploadFileLink.size})")
        return uploadFileSdk(
            uploadFileLink = uploadFileLink,
            uriString = uriString,
        ) { size ->
            setSize(size.value)
        }.fold(
            onFailure = { error ->
                if (error.handle(uploadFileLink)) {
                    Result.failure()
                } else {
                    if (error is UploadAbortedException) {
                        uploadFileLink.retryOrAbort(
                            retryable = error.cause?.isRetryable == true,
                            canRetry = canRetry(),
                            error = error,
                            message = "Uploading via SDK aborted"
                        ).also { result ->
                            if (result == Result.retry()) {
                                uploadSdkManager.cancelController(uploadFileLink)
                            }
                        }
                    } else {
                        uploadFileLink.retryOrAbort(
                            retryable = error.isRetryable,
                            canRetry = canRetry(),
                            error = error,
                            message = "Uploading via SDK failed"
                        )
                    }
                }
            },
            onSuccess = {
                CoreLogger.i(uploadFileLink.logTag(), "Upload via sdk succeed")
                Result.success(getSizeData(uploadFileLink.size?.value ?: 0))
            }
        )
    }

    private suspend fun Throwable.handle(uploadFileLink: UploadFileLink): Boolean =
        when (this) {
            is UploadNotFoundException -> {
                log(logTag(), "Upload not found, maybe after the app restarted")
                uploadFileLink.recreateFileSdk()
                true
            }
            else -> unwrapAborted().onProtonHttpException { protonData ->
                when (protonData.code) {
                    ALREADY_EXISTS,
                    NOT_EXISTS,
                        -> true.also {
                        log(logTag(), "Upload in error: ${protonData.code}, will retry")
                        uploadSdkManager.cancel(uploadFileLink)
                        uploadFileLink.recreateFileSdk()
                    }

                    else -> false
                }
            } ?: false
        }

    private fun Throwable.unwrapAborted(): Throwable = if (this is UploadAbortedException) {
        cause ?: this
    } else {
        this
    }

    private suspend fun UploadFileLink.recreateFileSdk() {
        val networkType =
            requireNotNull(networkTypeProviders[networkTypeProviderType])
                .get(parentLinkId)
        FileUploadFlow.RecreateFileSdk(
            workManager,
            this@UploadFileSdkWorker.userId,
            this.id,
            networkType,
            cleanupWorkers,
        ).enqueueWork(
            uploadTags = listOf(uploadFileLinkId.uniqueUploadWorkName),
            uriString = requireNotNull(this.uriString),
        )
    }

    companion object {
        fun getWorkRequest(
            userId: UserId,
            uploadFileLinkId: Long,
            uriString: String,
            networkType: NetworkType,
            tags: List<String> = emptyList(),
        ): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(UploadFileSdkWorker::class.java)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_USER_ID, userId.id)
                        .putLong(KEY_UPLOAD_FILE_LINK_ID, uploadFileLinkId)
                        .putString(KEY_URI_STRING, uriString)
                        .build()
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTags(listOf(userId.id) + tags)
                .build()
    }
}
