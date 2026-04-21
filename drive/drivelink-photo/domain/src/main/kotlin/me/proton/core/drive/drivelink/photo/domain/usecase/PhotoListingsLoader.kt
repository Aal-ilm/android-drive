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

package me.proton.core.drive.drivelink.photo.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.drivelink.photo.domain.entity.PhotoListingsSyncState
import me.proton.core.drive.drivelink.photo.domain.repository.PhotoListingAnchorRepository
import me.proton.core.drive.link.domain.entity.PhotoTag
import me.proton.core.drive.photo.domain.usecase.FetchAllPhotoListings
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.volume.domain.entity.VolumeId
import me.proton.core.util.kotlin.CoreLogger
import java.util.concurrent.ConcurrentHashMap

class PhotoListingsLoader(
    private val appScope: CoroutineScope,
    private val fetchAllPhotoListings: FetchAllPhotoListings,
    private val refreshPhotoListings: RefreshPhotoListings,
    private val photoListingAnchorRepository: PhotoListingAnchorRepository,
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val states = ConcurrentHashMap<String, MutableStateFlow<PhotoListingsSyncState>>()

    fun stateFor(key: String): StateFlow<PhotoListingsSyncState> = mutableStateFor(key)

    fun load(key: String, userId: UserId, volumeId: VolumeId, shareId: ShareId, tag: PhotoTag?) {
        appScope.launch {
            mutex.withLock {
                if (jobs[key]?.isActive == true) return@withLock
                startJob(key, userId, volumeId, shareId, tag)
            }
        }
    }

    fun retry(key: String, userId: UserId, volumeId: VolumeId, shareId: ShareId, tag: PhotoTag?) =
        load(key, userId, volumeId, shareId, tag)

    fun refresh(key: String, userId: UserId, volumeId: VolumeId, shareId: ShareId, tag: PhotoTag?) {
        CoreLogger.d(LogTag.PHOTO, "[$key] refresh requested")
        appScope.launch {
            mutex.withLock {
                jobs[key]?.cancel()
                jobs.remove(key)
                mutableStateFor(key).value = PhotoListingsSyncState.Idle
                refreshPhotoListings(userId, volumeId, tag)
                    .onFailure { error ->
                        CoreLogger.d(LogTag.PHOTO, error, "[$key] refresh cleanup failed")
                        mutableStateFor(key).value = PhotoListingsSyncState.Error(error)
                        return@withLock
                    }
                startJob(key, userId, volumeId, shareId, tag)
            }
        }
    }

    private fun mutableStateFor(key: String): MutableStateFlow<PhotoListingsSyncState> =
        states.getOrPut(key) { MutableStateFlow(PhotoListingsSyncState.Idle) }

    private fun startJob(key: String, userId: UserId, volumeId: VolumeId, shareId: ShareId, tag: PhotoTag?) {
        mutableStateFor(key).value = PhotoListingsSyncState.Loading
        jobs[key] = appScope.launch {
            val anchor = photoListingAnchorRepository.getAnchor(key, userId, volumeId)
            if (anchor?.isComplete == true) {
                CoreLogger.d(LogTag.PHOTO, "[$key] already complete, skipping load")
                mutableStateFor(key).value = PhotoListingsSyncState.Complete
                return@launch
            }
            CoreLogger.d(LogTag.PHOTO, "[$key] load started (anchor.nextKey=${anchor?.nextKey})")
            var totalLoaded = 0
            fetchAllPhotoListings(
                userId = userId,
                volumeId = volumeId,
                shareId = shareId,
                tag = tag,
                initialPageKey = anchor?.nextKey,
                postPageProcess = { nextKey, isComplete, pageCount ->
                    totalLoaded += pageCount
                    photoListingAnchorRepository.upsertAnchor(
                        userId = userId,
                        key = key,
                        volumeId = volumeId,
                        nextKey = nextKey,
                        isComplete = isComplete,
                    )
                },
            )
                .onSuccess {
                    CoreLogger.d(LogTag.PHOTO, "[$key] load complete, total=$totalLoaded")
                    mutableStateFor(key).value = PhotoListingsSyncState.Complete
                }
                .onFailure { error ->
                    CoreLogger.d(LogTag.PHOTO, error, "[$key] load failed after $totalLoaded items")
                    mutableStateFor(key).value = PhotoListingsSyncState.Error(error)
                }
        }
    }
}
