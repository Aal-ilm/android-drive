/*
 * Copyright (c) 2026 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.drive.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.android.drive.document.scanner.domain.entity.ScanResult
import me.proton.android.drive.document.scanner.domain.entity.ScannerOptions
import me.proton.android.drive.document.scanner.domain.extension.fileName
import me.proton.android.drive.document.scanner.domain.extension.toMimeType
import me.proton.android.drive.document.scanner.domain.usecase.ClearScanResult
import me.proton.android.drive.document.scanner.domain.usecase.GetScanResult
import me.proton.android.drive.document.scanner.domain.usecase.RemoveScanResult
import me.proton.android.drive.document.scanner.presentation.entity.ScannerOutputFormat
import me.proton.android.drive.document.scanner.presentation.viewevent.ScanDocumentNameViewEvent
import me.proton.android.drive.document.scanner.presentation.viewstate.ScanDocumentNameViewState
import me.proton.android.drive.extension.getDefaultMessage
import me.proton.android.drive.extension.log
import me.proton.core.drive.base.domain.entity.TimestampMs
import me.proton.core.drive.base.domain.extension.bytes
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.log.LogTag.VIEW_MODEL
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.usecase.BroadcastMessages
import me.proton.core.drive.base.presentation.extension.require
import me.proton.core.drive.base.presentation.viewmodel.UserViewModel
import me.proton.core.drive.drivelink.crypto.domain.usecase.GetDecryptedDriveLink
import me.proton.core.drive.drivelink.upload.domain.usecase.UploadFiles
import me.proton.core.drive.link.domain.entity.FolderId
import me.proton.core.drive.linkupload.domain.entity.UploadFileDescription
import me.proton.core.drive.linkupload.domain.entity.UploadFileLink
import me.proton.core.drive.linkupload.domain.entity.UploadFileProperties
import me.proton.core.drive.messagequeue.domain.entity.BroadcastMessage
import me.proton.core.drive.share.domain.entity.ShareId
import javax.inject.Inject
import me.proton.core.drive.i18n.R as I18N

@HiltViewModel
class ScanDocumentNameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val appContext: Context,
    private val getDriveLink: GetDecryptedDriveLink,
    private val getScanResult: GetScanResult,
    private val uploadFiles: UploadFiles,
    private val removeScanResult: RemoveScanResult,
    private val clearScanResult: ClearScanResult,
    private val broadcastMessages: BroadcastMessages,
    private val configurationProvider: ConfigurationProvider,
) : ViewModel(), UserViewModel by UserViewModel(savedStateHandle) {
    private val folderId: FolderId =
        FolderId(
            shareId = ShareId(userId, savedStateHandle.require(KEY_SHARE_ID)),
            id = savedStateHandle.require(KEY_FOLDER_ID),
        )
    private val scanResultId: Long = savedStateHandle.require(KEY_SCAN_RESULT_ID)
    private val basename: String = savedStateHandle.get<String>(KEY_BASENAME) ?: ""
    private val currentDocumentName = MutableStateFlow(basename)
    private val pdfOutputFormat = ScannerOutputFormat(
        outputFormat = ScannerOptions.OutputFormat.PDF,
        title = appContext.getString(I18N.string.scan_document_name_output_format_pdf_title),
        description = appContext.getString(I18N.string.scan_document_name_output_format_pdf_description),
    )
    private val jpegOutputFormat = ScannerOutputFormat(
        outputFormat = ScannerOptions.OutputFormat.JPEG,
        title = appContext.getString(I18N.string.scan_document_name_output_format_jpeg_title),
        description = appContext.getString(I18N.string.scan_document_name_output_format_jpeg_description),
    )
    private val outputFormats: List<ScannerOutputFormat> = listOf(
        pdfOutputFormat, jpegOutputFormat
    )
    private val selected: MutableStateFlow<List<ScannerOutputFormat>> = MutableStateFlow(listOf(pdfOutputFormat))
    val initialViewState = ScanDocumentNameViewState(
        isDoneEnabled = false,
        isScheduleUploadInProgress = false,
        doneButtonLabelResId = I18N.string.common_done_action,
        titleResId = I18N.string.scan_document_name_title,
        name = currentDocumentName,
        selectAllName = true,
        outputFormats = outputFormats,
        selectedOutputFormats = selected.value,
    )
    private val isScheduleUploadInProgress = MutableStateFlow(false)
    private val isDoneEnabled = currentDocumentName.map {
        it.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val viewState: Flow<ScanDocumentNameViewState> = combine(
        isDoneEnabled,
        isScheduleUploadInProgress,
        currentDocumentName,
        selected,
    ) { isEnabled, isInProgress, documentName, selectedOutputFormats ->
        initialViewState.copy(
            selectAllName = documentName.equals(other = basename, ignoreCase = true),
            isDoneEnabled = isEnabled && selectedOutputFormats.isNotEmpty(),
            isScheduleUploadInProgress = isInProgress,
            selectedOutputFormats = selectedOutputFormats,
        )
    }

    fun viewEvent(
        navigateBack: () -> Unit,
    ): ScanDocumentNameViewEvent = object : ScanDocumentNameViewEvent {
        override val onBackPressed = { onCancel(navigateBack) }
        override val onDone = { onDone(navigateBack) }
        override val onNameChanged = ::onChanged
        override val onScannerOutputFormat = { outputFormat: ScannerOutputFormat -> onToggle(outputFormat) }
    }

    private fun onChanged(name: String) {
        viewModelScope.launch {
            currentDocumentName.value = name
        }
    }

    private fun onToggle(outputFormat: ScannerOutputFormat) {
        if (outputFormat in selected.value) {
            selected.value -= outputFormat
        } else {
            selected.value += outputFormat
        }
    }

    private fun onDone(navigateBack: () -> Unit) {
        viewModelScope.launch {
            isScheduleUploadInProgress.value = true
            val folder = getDriveLink(userId, folderId)
                .toResult()
                .onFailure { error ->
                    error.showOnDoneError()
                    return@launch
                }
                .getOrThrow()
            val scanResult = getScanResult(userId, scanResultId)
                .onFailure { error ->
                    error.showOnDoneError()
                    return@launch
                }
                .getOrThrow()
            val basename = currentDocumentName.value
            val document = if (pdfOutputFormat in selected.value) {
                listOfNotNull(scanResult.document?.toUploadFileDescription(basename))
            } else {
                scanResult.document?.file?.delete()
                emptyList()
            }
            val pages = if (jpegOutputFormat in selected.value) {
                scanResult.pages.map { page -> page.toUploadFileDescription(basename) }
            } else {
                scanResult.pages.forEach { page -> page.file.delete() }
                emptyList()
            }
            uploadFiles(
                folder = folder,
                uploadFileDescriptions = document + pages,
                shouldDeleteSource = true,
                priority = UploadFileLink.USER_PRIORITY,
                background = true,
            )
                .onFailure { error ->
                    error.showOnDoneError()
                    return@launch
                }
            removeScanResult(userId, scanResult.id).onFailure { error ->
                error.log(VIEW_MODEL, "Failed to remove scan result")
            }
            navigateBack()
        }
    }

    private fun onCancel(navigateBack: () -> Unit) {
        viewModelScope.launch {
            clearScanResult(userId, scanResultId).onFailure { error ->
                error.log(VIEW_MODEL, "Failed clearing scan result $scanResultId")
            }
            navigateBack()
        }
    }

    private fun ScanResult.Output.Document.toUploadFileDescription(
        basename: String,
    ) = UploadFileDescription(
        uri = Uri.fromFile(file).toString(),
        properties = UploadFileProperties(
            name = fileName(basename),
            mimeType = format.toMimeType(),
            size = file.length().bytes,
            lastModified = TimestampMs(file.lastModified()),
        )
    )

    private fun ScanResult.Output.Page.toUploadFileDescription(
        basename: String,
    ) = UploadFileDescription(
        uri = Uri.fromFile(file).toString(),
        properties = UploadFileProperties(
            name = fileName(basename),
            mimeType = format.toMimeType(),
            size = file.length().bytes,
            lastModified = TimestampMs(file.lastModified()),
        )
    )

    private fun Throwable.showOnDoneError(
        message: String = getDefaultMessage(
            context = appContext,
            useExceptionMessage = configurationProvider.useExceptionMessage,
        )
    ) {
        log(tag = VIEW_MODEL, message = message)
        isScheduleUploadInProgress.value = false
        broadcastMessages(
            userId = userId,
            message = message,
            type = BroadcastMessage.Type.ERROR,
        )
    }

    companion object {
        const val KEY_SHARE_ID = "shareId"
        const val KEY_FOLDER_ID = "folderId"
        const val KEY_BASENAME = "basename"
        const val KEY_SCAN_RESULT_ID = "scanResultId"
    }
}
