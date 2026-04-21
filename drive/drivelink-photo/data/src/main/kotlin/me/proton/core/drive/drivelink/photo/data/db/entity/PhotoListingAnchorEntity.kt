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

package me.proton.core.drive.drivelink.photo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.data.db.Column
import me.proton.core.drive.base.data.db.Column.IS_COMPLETE
import me.proton.core.drive.base.data.db.Column.KEY
import me.proton.core.drive.base.data.db.Column.NEXT_KEY
import me.proton.core.drive.base.data.db.Column.USER_ID
import me.proton.core.drive.base.data.db.Column.VOLUME_ID

@Entity(
    primaryKeys = [KEY, VOLUME_ID],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = [Column.Core.USER_ID],
            childColumns = [USER_ID],
            onDelete = ForeignKey.CASCADE
        ),
    ],
)
data class PhotoListingAnchorEntity(
    @ColumnInfo(name = USER_ID)
    val userId: UserId,
    @ColumnInfo(name = KEY)
    val key: String,
    @ColumnInfo(name = VOLUME_ID)
    val volumeId: String,
    @ColumnInfo(name = NEXT_KEY)
    val nextKey: String?,
    @ColumnInfo(name = IS_COMPLETE)
    val isComplete: Boolean,
)
