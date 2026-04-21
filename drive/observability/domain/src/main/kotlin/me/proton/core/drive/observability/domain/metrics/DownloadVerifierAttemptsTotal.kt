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

package me.proton.core.drive.observability.domain.metrics

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import me.proton.core.drive.observability.domain.metrics.common.BooleanStatus
import me.proton.core.observability.domain.entity.SchemaId

typealias StringBuckets = String

@Serializable
@Schema(description = "Drive file download verification")
@SchemaId("https://proton.me/drive_download_verifier_attempts_total_v2.schema.json")
data class DownloadVerifierAttemptsTotal(
    override val Labels: LabelsData,
    @Required override val Value: Long = 1,
) : DriveObservabilityData() {

    @Serializable
    data class LabelsData(
        val result: ResultStatusWithSkipped,
        val fileSize: StringBuckets,
        val checksumVerified: BooleanStatus,
    )

    @Suppress("EnumEntryName")
    enum class ResultStatusWithSkipped {
        success,
        failure,
        skipped,
    }
}
