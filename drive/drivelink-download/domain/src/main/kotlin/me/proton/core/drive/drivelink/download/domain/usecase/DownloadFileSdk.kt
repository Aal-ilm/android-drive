/*
 * Copyright (c) 2025 Proton AG.
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

package me.proton.core.drive.drivelink.download.domain.usecase

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import me.proton.android.drive.verifier.domain.exception.ContentDigestVerifierException
import me.proton.core.drive.base.domain.entity.Percentage
import me.proton.core.drive.base.domain.extension.bytes
import me.proton.core.drive.base.domain.extension.getOrNull
import me.proton.core.drive.base.domain.extension.toPercentage
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.formatter.DateTimeFormatter
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.log.LogTag.DOWNLOAD
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.usecase.GetDownloadStagingTempFolder
import me.proton.core.drive.base.domain.usecase.ReportError
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.drivelink.crypto.domain.usecase.IntegrityMetricsNotifier
import me.proton.core.drive.drivelink.domain.extension.decryptedFileName
import me.proton.core.drive.drivelink.domain.usecase.GetDriveLink
import me.proton.core.drive.drivelink.domain.usecase.GetVolumeType
import me.proton.core.drive.drivelink.download.domain.manager.DownloadSdkManager
import me.proton.core.drive.file.base.domain.extension.captureTime
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.link.domain.extension.nodeUid
import me.proton.core.drive.link.domain.extension.revisionUid
import me.proton.core.drive.link.domain.extension.userId
import me.proton.core.drive.linkdownload.domain.entity.DownloadState
import me.proton.core.drive.linkdownload.domain.usecase.RemoveSignatureVerificationFailed
import me.proton.core.drive.linkdownload.domain.usecase.SetDownloadState
import me.proton.core.drive.linkdownload.domain.usecase.SetSignatureVerificationFailed
import me.proton.core.drive.thumbnail.domain.usecase.GetThumbnailPermanentFile
import me.proton.core.drive.volume.domain.entity.Volume
import me.proton.core.drive.volume.domain.entity.VolumeId
import me.proton.core.util.kotlin.CoreLogger
import me.proton.drive.sdk.DownloadController
import me.proton.drive.sdk.ProtonDriveSdkException
import me.proton.drive.sdk.ProtonSdkError
import me.proton.drive.sdk.ProtonSdkError.ErrorDomain
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

class DownloadFileSdk @Inject constructor(
    private val downloadSdkManager: DownloadSdkManager,
    private val setDownloadState: SetDownloadState,
    private val getThumbnailPermanentFile: GetThumbnailPermanentFile,
    private val moveFileIfExists: MoveFileIfExists,
    private val getDriveLink: GetDriveLink,
    private val getVolumeType: GetVolumeType,
    private val getDownloadStagingTempFolder: GetDownloadStagingTempFolder,
    private val setSignatureVerificationFailed: SetSignatureVerificationFailed,
    private val removeSignatureVerificationFailed: RemoveSignatureVerificationFailed,
    private val verifyDownloadedFile: VerifyDownloadedFile,
    private val integrityMetricsNotifier: IntegrityMetricsNotifier,
    private val configurationProvider: ConfigurationProvider,
    private val reportError: ReportError,
    private val dateTimeFormatter: DateTimeFormatter,
) {

    suspend operator fun invoke(
        volumeId: VolumeId,
        fileId: FileId,
        revisionId: String,
        progress: MutableStateFlow<Percentage>,
    ): Result<Unit> {
        var file: File?
        var tmpFile: File?
        return coRunCatching {
            file = moveFileIfExists(fileId).getOrThrow()
            val driveLink = getDriveLink(fileId).toResult().getOrThrow()
            val volumeType = getVolumeType(driveLink).getOrThrow()
            getThumbnailPermanentFile(volumeId, driveLink.link, revisionId).getOrThrow()
            if (file.exists()) {
                CoreLogger.d(DOWNLOAD, "File already downloaded")
                return@coRunCatching
            }

            val volumeId = driveLink.volumeId
            val fileId = driveLink.id

            tmpFile = File(
                getDownloadStagingTempFolder(userId = fileId.userId, volumeId.id, revisionId),
                driveLink.decryptedFileName,
            )
            coroutineScope {
                when (volumeType) {
                    Volume.Type.PHOTO -> {
                        downloadSdkManager.enqueuePhoto(
                            volumeId = driveLink.volumeId,
                            fileId = fileId,
                            revisionId = revisionId,
                        ) { client ->
                            client.downloader(
                                photoUid = fileId.nodeUid(driveLink.volumeId),
                                timeout = configurationProvider.sdkQueueTimeout,
                            )
                        }
                    }

                    Volume.Type.REGULAR -> {
                        downloadSdkManager.enqueueFile(
                            volumeId = volumeId,
                            fileId = fileId,
                            revisionId = revisionId,
                        ) { client ->
                            client.downloader(
                                revisionUid = fileId.revisionUid(
                                    volumeId = volumeId,
                                    revisionId = revisionId,
                                ),
                                timeout = configurationProvider.sdkQueueTimeout,
                            )
                        }
                    }

                    else -> error("Cannot download link for volume type: $volumeType")
                }

                val controller = downloadSdkManager.controller(
                    volumeId = driveLink.volumeId,
                    fileId = fileId,
                    revisionId = revisionId,
                ) { downloader ->
                    downloader.downloadToStream(
                        coroutineScope = this,
                        channel = tmpFile.outputStream().channel
                    )
                }
                val job = controller.progressFlow
                    .filterNotNull()
                    .map { progressUpdate -> progressUpdate.toPercentage() }
                    .onEach(progress::tryEmit)
                    .launchIn(this)
                controller.tryResume(this)
                try {
                    removeSignatureVerificationFailed(fileId).getOrNull(
                        tag = DOWNLOAD,
                        message = "Failed to remove that signature verification failed"
                    )
                    controller.awaitCompletion()
                } catch (e: ProtonDriveSdkException) {
                    val error = e.error
                    if (controller.isNotVerified(error)) {
                        CoreLogger.w(
                            DOWNLOAD,
                            e,
                            "File downloaded but not verified, continuing"
                        )
                        setSignatureVerificationFailed(fileId).getOrNull(
                            tag = DOWNLOAD,
                            message = "Failed to set that signature verification failed"
                        )
                    } else {
                        throw e
                    }
                } finally {
                    job.cancel()
                }
            }

            Files.move(
                tmpFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            if (verifyDownloadedFile.isAllowed(userId = driveLink.userId)) {
                val checksumVerified = driveLink.link.activeRevisionChecksumVerified
                verifyDownloadedFile(
                    driveLink = driveLink,
                    file = file,
                )
                    .onSuccess {
                        integrityMetricsNotifier.downloadVerifier(
                            fileSize = file.length().bytes,
                            isSuccess = true,
                            checksumVerified = checksumVerified,
                        )
                    }
                    .recoverCatching { error ->
                        if (error is ContentDigestVerifierException.Mismatch) {
                            val fileSize = file.length().bytes
                            integrityMetricsNotifier.downloadVerifier(
                                fileSize = fileSize,
                                isSuccess = false,
                                checksumVerified = checksumVerified,
                                throwable = error,
                            )
                            if (checksumVerified) {
                                file.delete()
                                throw error
                            } else {
                                reportError(
                                    tag = LogTag.DOWNLOAD,
                                    error = error,
                                    message = buildString {
                                        append("ContentDigestVerifierException.Mismatch ChecksumVerified=false, ")
                                        append("revisionId=${revisionId}, ")
                                        append("creationTime=${driveLink.link.creationTime.captureTime(dateTimeFormatter)}")
                                    },
                                )
                            }
                        } else {
                            integrityMetricsNotifier.downloadVerifier(
                                fileSize = file.length().bytes,
                                isSuccess = true,
                                checksumVerified = checksumVerified,
                                throwable = error,
                            )
                        }
                    }
                    .getOrThrow()
            }
        }.onFailure { error ->
            reportError(
                tag = DOWNLOAD,
                error = error,
                message = "Cannot download ${fileId.id} (sdk)",
            )
        }
    }

    private suspend fun DownloadController.isNotVerified(error: ProtonSdkError?): Boolean {
        return error != null
                && error.domain == ErrorDomain.DataIntegrity
                && isDownloadCompleteWithVerificationIssue()
    }
}
