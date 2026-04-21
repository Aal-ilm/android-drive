/*
 * Copyright (c) 2023 Proton AG.
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

package me.proton.android.drive.photos.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.link.domain.entity.PhotoTag
import me.proton.core.drive.photo.domain.entity.PhotoListing
import me.proton.core.drive.photo.domain.repository.PhotoRepository
import me.proton.core.drive.photo.domain.usecase.FetchAllPhotoListings
import me.proton.core.drive.share.crypto.domain.usecase.GetPhotoShare
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.volume.domain.entity.VolumeId
import javax.inject.Inject

class FetchAllPhotoListingsImpl @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val getPhotoShare: GetPhotoShare,
    private val configurationProvider: ConfigurationProvider,
) : FetchAllPhotoListings {

    override suspend operator fun invoke(
        userId: UserId,
        volumeId: VolumeId,
        pageSize: Int,
        linkId: FileId?,
    ): Result<List<PhotoListing>> = coRunCatching {
        val share = getPhotoShare(userId, volumeId).toResult().getOrThrow()
        val photoListings = mutableListOf<PhotoListing>()
        fetchPages(
            userId = userId,
            volumeId = volumeId,
            shareId = share.id,
            pageSize = pageSize,
            initialPageKey = linkId?.id,
            tag = null,
        ) { fetched, _, _ ->
            photoListings += fetched
        }
        photoListings
    }

    override suspend operator fun invoke(
        userId: UserId,
        volumeId: VolumeId,
        shareId: ShareId,
        tag: PhotoTag?,
        initialPageKey: String?,
        postPageProcess: suspend (nextKey: String?, isComplete: Boolean, pageCount: Int) -> Unit,
    ): Result<Unit> = coRunCatching {
        fetchPages(
            userId = userId,
            volumeId = volumeId,
            shareId = shareId,
            pageSize = configurationProvider.apiListingPageSize,
            initialPageKey = initialPageKey,
            tag = tag,
        ) { fetched, nextKey, endOfPagination ->
            postPageProcess(
                if (endOfPagination) null else nextKey,
                endOfPagination,
                fetched.size,
            )
        }
    }

    private suspend fun fetchPages(
        userId: UserId,
        volumeId: VolumeId,
        shareId: ShareId,
        pageSize: Int,
        initialPageKey: String?,
        tag: PhotoTag?,
        onPage: suspend (fetched: List<PhotoListing>, nextKey: String?, endOfPagination: Boolean) -> Unit,
    ) {
        var pageKey = initialPageKey
        while (true) {
            val fetched = photoRepository.fetchAndStorePhotoListings(
                userId = userId,
                volumeId = volumeId,
                shareId = shareId,
                pageSize = pageSize,
                previousPageLastLinkId = pageKey,
                tag = tag,
            )
            val endOfPagination = fetched.size < pageSize
            val nextKey = fetched.lastOrNull()?.linkId?.id
            onPage(fetched, nextKey, endOfPagination)
            if (endOfPagination) break
            pageKey = nextKey
        }
    }
}
