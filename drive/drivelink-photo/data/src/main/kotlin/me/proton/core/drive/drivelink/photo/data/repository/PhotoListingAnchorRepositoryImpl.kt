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

package me.proton.core.drive.drivelink.photo.data.repository

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.drivelink.photo.data.db.DriveLinkPhotoDatabase
import me.proton.core.drive.drivelink.photo.data.db.entity.PhotoListingAnchorEntity
import me.proton.core.drive.drivelink.photo.data.extension.toPhotoListingAnchor
import me.proton.core.drive.drivelink.photo.domain.entity.PhotoListingAnchor
import me.proton.core.drive.drivelink.photo.domain.repository.PhotoListingAnchorRepository
import me.proton.core.drive.volume.domain.entity.VolumeId
import javax.inject.Inject

class PhotoListingAnchorRepositoryImpl @Inject constructor(
    private val db: DriveLinkPhotoDatabase,
) : PhotoListingAnchorRepository {

    override suspend fun getAnchor(key: String, userId: UserId, volumeId: VolumeId): PhotoListingAnchor? =
        db.photoListingAnchorDao.getAnchor(key, userId, volumeId.id)?.toPhotoListingAnchor()

    override suspend fun upsertAnchor(
        key: String,
        userId: UserId,
        volumeId: VolumeId,
        nextKey: String?,
        isComplete: Boolean,
    ) {
        db.photoListingAnchorDao.insertOrUpdate(
            PhotoListingAnchorEntity(
                userId = userId,
                key = key,
                volumeId = volumeId.id,
                nextKey = nextKey,
                isComplete = isComplete,
            )
        )
    }

    override suspend fun deleteAnchor(key: String, userId: UserId, volumeId: VolumeId) {
        db.photoListingAnchorDao.deleteAnchor(key, userId, volumeId.id)
    }
}
