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

package me.proton.core.drive.drivelink.photo.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.proton.core.drive.drivelink.photo.domain.repository.PhotoListingAnchorRepository
import me.proton.core.drive.drivelink.photo.domain.usecase.PhotoListingsLoader
import me.proton.core.drive.drivelink.photo.domain.usecase.RefreshPhotoListings
import me.proton.core.drive.photo.domain.usecase.FetchAllPhotoListings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DriveLinkPhotoModule {

    @Provides
    @Singleton
    fun providePhotoListingsLoader(
        fetchAllPhotoListings: FetchAllPhotoListings,
        refreshPhotoListings: RefreshPhotoListings,
        photoListingAnchorRepository: PhotoListingAnchorRepository,
    ): PhotoListingsLoader = PhotoListingsLoader(
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        fetchAllPhotoListings = fetchAllPhotoListings,
        refreshPhotoListings = refreshPhotoListings,
        photoListingAnchorRepository = photoListingAnchorRepository,
    )
}
