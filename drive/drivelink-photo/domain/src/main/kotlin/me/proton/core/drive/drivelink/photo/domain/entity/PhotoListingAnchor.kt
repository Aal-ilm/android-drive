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

package me.proton.core.drive.drivelink.photo.domain.entity

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.link.domain.entity.PhotoTag
import me.proton.core.drive.volume.domain.entity.VolumeId

data class PhotoListingAnchor(
    val userId: UserId,
    val key: String,
    val volumeId: VolumeId,
    val nextKey: String?,
    val isComplete: Boolean,
) {
    companion object {
        const val mainKey = "PHOTO_LISTING"
        fun tagKey(tag: PhotoTag) = "PHOTO_LISTING_$tag"
    }
}
