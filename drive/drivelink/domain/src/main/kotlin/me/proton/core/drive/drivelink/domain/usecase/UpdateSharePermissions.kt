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

package me.proton.core.drive.drivelink.domain.usecase

import me.proton.core.drive.base.domain.entity.Permissions
import me.proton.core.drive.base.domain.extension.getOrNull
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.log.LogTag.SHARING
import me.proton.core.drive.drivelink.domain.entity.DriveLink
import me.proton.core.drive.link.domain.extension.shareId
import me.proton.core.drive.link.domain.extension.userId
import me.proton.core.drive.linknode.domain.usecase.GetLinkAncestors
import me.proton.core.drive.share.domain.entity.Share
import me.proton.core.drive.share.domain.usecase.GetHighestSharePermissions
import me.proton.core.drive.share.domain.usecase.GetShare
import me.proton.core.drive.volume.domain.entity.Volume
import me.proton.core.drive.volume.domain.usecase.GetActiveVolumes
import javax.inject.Inject

class UpdateSharePermissions @Inject constructor(
    private val getActiveVolumes: GetActiveVolumes,
    private val getShare: GetShare,
    private val getLinkAncestors: GetLinkAncestors,
    private val getHighestSharePermissions: GetHighestSharePermissions,
) {
    suspend operator fun invoke(driveLink: DriveLink): DriveLink {
        val share = getShare(driveLink.shareId).toResult()
            .getOrNull(SHARING, "Cannot find share")
            ?: return driveLink
        val sharePermissions = when (share.type) {
            Share.Type.STANDARD -> {
                getPermissions(
                    share = share,
                    driveLink = driveLink,
                    volumes = getActiveVolumes(driveLink.userId)
                        .toResult()
                        .getOrNull(SHARING, "Cannot get main share")
                        .orEmpty(),
                )
            }
            else -> Permissions.owner
        }
        return driveLink.copySharePermission(sharePermissions)
    }

    suspend operator fun invoke(driveLinks: List<DriveLink>): List<DriveLink> {
        val volumesByUserId = driveLinks.map { it.userId }.distinct()
            .associateWith { userId -> getActiveVolumes(userId).toResult().getOrNull(SHARING, "Cannot get volumes") }
        val shareByShareId = driveLinks.map { it.shareId }.distinct()
            .associateWith { shareId -> getShare(shareId).toResult().getOrNull(SHARING, "Cannot find share") }
        return driveLinks.map { driveLink ->
            shareByShareId[driveLink.shareId]?.let{ share ->
                val sharePermissions = when (share.type) {
                    Share.Type.STANDARD -> getPermissions(
                        share = share,
                        driveLink = driveLink,
                        volumes = volumesByUserId[driveLink.userId].orEmpty(),
                    )
                    else -> Permissions.owner
                }
                driveLink.copySharePermission(sharePermissions)
            } ?: driveLink
        }
    }

    private suspend fun getPermissions(
        share: Share,
        driveLink: DriveLink,
        volumes: List<Volume>,
    ): Permissions = share.checkVolumes(volumes)
        ?: checkLinks(driveLink)

    private suspend fun checkLinks(driveLink: DriveLink): Permissions =
        getLinkAncestors(driveLink.id).toResult().getOrNull()?.mapNotNull { link ->
            link.sharingDetails?.shareId
        }.orEmpty().let { shareIds ->
            getHighestSharePermissions(shareIds).getOrNull(SHARING, "Cannot get share permissions")
        } ?: Permissions.viewer

    private fun Share.checkPhotoShare(photoShare: Share?): Permissions? =
        Permissions.owner.takeIf { volumeId == photoShare?.volumeId }

    private fun Share.checkVolumes(volumes: List<Volume>): Permissions? =
        Permissions.owner.takeIf { volumes.any { volume -> volume.id == volumeId } }

    private fun DriveLink.copySharePermission(
        sharePermissions: Permissions
    ): DriveLink = when (this) {
        is DriveLink.Folder -> this.copy(sharePermissions = sharePermissions)
        is DriveLink.File -> this.copy(sharePermissions = sharePermissions)
        is DriveLink.Album -> this.copy(sharePermissions = sharePermissions)
    }
}
