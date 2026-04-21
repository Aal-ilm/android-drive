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

package me.proton.core.drive.drivelink.rename.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.provider.ProtonDriveClientProvider
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.eventmanager.base.domain.usecase.UpdateEventAction
import me.proton.core.drive.link.domain.usecase.ValidateLinkName
import me.proton.drive.sdk.entity.NodeUid
import javax.inject.Inject

class RenameLinkSdk @Inject constructor(
    private val protonDriveClientProvider: ProtonDriveClientProvider,
    private val updateEventAction: UpdateEventAction,
    private val validateLinkName: ValidateLinkName,
) {
    suspend operator fun invoke(
        userId: UserId,
        nodeUid: NodeUid,
        linkName: String,
    ): Result<Unit> = coRunCatching {
        // TODO: Remove when SDK validate name
        val validatedName = validateLinkName(linkName).getOrThrow()

        updateEventAction(
            userId = userId,
            nodeUid = nodeUid,
        ) {
            protonDriveClientProvider
                .getOrCreate(userId)
                .getOrThrow()
                .rename(
                    nodeUid = nodeUid,
                    name = validatedName,
                )
        }
    }
}
