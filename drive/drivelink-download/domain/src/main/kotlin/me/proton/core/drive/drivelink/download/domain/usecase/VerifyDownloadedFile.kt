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

package me.proton.core.drive.drivelink.download.domain.usecase

import me.proton.android.drive.verifier.domain.usecase.VerifyContentDigest
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.extension.getOrNull
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.drivelink.crypto.domain.usecase.GetContentDigest
import me.proton.core.drive.feature.flag.domain.entity.FeatureFlagId.Companion.driveDownloadVerificationDisabled
import me.proton.core.drive.feature.flag.domain.extension.off
import me.proton.core.drive.feature.flag.domain.usecase.GetFeatureFlag
import java.io.File
import javax.inject.Inject

class VerifyDownloadedFile @Inject constructor(
    private val verifyContentDigest: VerifyContentDigest,
    private val getContentDigest: GetContentDigest,
    private val getFeatureFlag: GetFeatureFlag,
) {

    suspend operator fun invoke(
        driveLink: DriveLink.File,
        file: File,
    ): Result<Unit> = coRunCatching {
        verifyContentDigest(
            claimed = getContentDigest(driveLink).getOrNull(
                tag = LogTag.DOWNLOAD,
                message = "Failed to get content digest",
            ).orEmpty(),
            file = file,
        ).getOrThrow()
    }

    suspend fun isAllowed(userId: UserId): Boolean =
        getFeatureFlag(
            featureFlagId = driveDownloadVerificationDisabled(userId),
        ).off
}
