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

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.transformLatest
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.drive.base.domain.provider.ConfigurationProvider
import me.proton.core.drive.base.domain.usecase.ReportError
import me.proton.core.drive.link.domain.entity.PhotoTag
import me.proton.core.drive.photo.domain.entity.PhotoListing
import me.proton.core.drive.photo.domain.repository.PhotoRepository
import me.proton.core.drive.volume.domain.entity.VolumeId
import javax.inject.Inject

class GetPhotoListingsPagingData @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val configurationProvider: ConfigurationProvider,
    private val reportError: ReportError,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun <T : Any> invoke(
        userId: UserId,
        volumeId: VolumeId,
        tag: PhotoTag?,
        transform: suspend (PhotoListing) -> T,
    ): Flow<PagingData<T>> =
        photoRepository.getPhotoListingCount(userId, volumeId, tag)
            .debounce(300)
            .transformLatest { count ->
                if (count == 0) emit(pagingDataLoading())
                try {
                    val items = loadAllPages(userId, volumeId, tag, transform)
                    emit(pagingDataComplete(items))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    System.gc()
                    reportError(LogTag.PHOTO, e, "OutOfMemoryError occurred while loading $count photo listings from DB")
                    emit(pagingDataError(e))
                } catch (e: Exception) {
                    emit(pagingDataError(e))
                }
            }

    private fun <T : Any> pagingDataLoading(): PagingData<T> = PagingData.from(
        data = emptyList(),
        sourceLoadStates = LoadStates(
            refresh = LoadState.Loading,
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        ),
    )

    private fun <T : Any> pagingDataComplete(items: List<T>): PagingData<T> = PagingData.from(
        data = items,
        sourceLoadStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        ),
    )

    private fun <T : Any> pagingDataError(e: Throwable): PagingData<T> = PagingData.from(
        data = emptyList(),
        sourceLoadStates = LoadStates(
            refresh = LoadState.Error(e),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        ),
    )

    private suspend fun <T : Any> loadAllPages(
        userId: UserId,
        volumeId: VolumeId,
        tag: PhotoTag?,
        transform: suspend (PhotoListing) -> T,
    ): List<T> {
        val result = mutableListOf<T>()
        var offset = 0
        while (true) {
            val page = photoRepository.getPhotoListings(
                userId = userId,
                volumeId = volumeId,
                fromIndex = offset,
                count = configurationProvider.dbPageSize,
                tag = tag,
            )
            page.mapTo(result) { transform(it) }
            if (page.size < configurationProvider.dbPageSize) break
            offset += configurationProvider.dbPageSize
        }
        return result
    }
}
