/*
 * Copyright (c) 2026 Proton AG.
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

package me.proton.core.drive.drivelink.crypto.data.usecase

import me.proton.core.drive.base.domain.entity.Bytes
import me.proton.core.drive.drivelink.crypto.domain.usecase.IntegrityMetricsNotifier
import me.proton.core.drive.observability.data.extension.toStringBuckets
import me.proton.core.drive.observability.domain.metrics.DownloadVerifierAttemptsTotal
import me.proton.core.drive.observability.domain.metrics.common.BooleanStatus
import me.proton.core.drive.observability.domain.usecase.EnqueueObservabilityEvent
import javax.inject.Inject

class IntegrityMetricsNotifierImpl @Inject constructor(
    private val enqueueObservabilityEvent: EnqueueObservabilityEvent,
) : IntegrityMetricsNotifier {

    override suspend fun downloadVerifier(
        fileSize: Bytes,
        isSuccess: Boolean,
        checksumVerified: Boolean,
        throwable: Throwable?
    ) {
        enqueueObservabilityEvent(
            DownloadVerifierAttemptsTotal(
                Labels = DownloadVerifierAttemptsTotal.LabelsData(
                    result = when {
                        isSuccess && throwable == null -> DownloadVerifierAttemptsTotal.ResultStatusWithSkipped.success
                        isSuccess && throwable != null -> DownloadVerifierAttemptsTotal.ResultStatusWithSkipped.skipped
                        else -> DownloadVerifierAttemptsTotal.ResultStatusWithSkipped.failure
                    },
                    fileSize = fileSize.toStringBuckets(),
                    checksumVerified = if (checksumVerified) BooleanStatus.`true` else BooleanStatus.`false`,
                )
            )
        )
    }
}
