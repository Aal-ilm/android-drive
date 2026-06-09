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

package me.proton.core.drive.base.data.extension

import me.proton.core.drive.base.domain.entity.TimestampMs
import me.proton.core.drive.base.domain.entity.TimestampS
import me.proton.core.drive.base.domain.entity.toTimestampMs
import java.time.Instant
import java.time.ZoneOffset

/**
 * `Long` should be number of milliseconds or seconds since Unix epoch
 */
val Long.convertToTimestampMs: TimestampMs? get() = takeIf { this >= 0 }
    ?.let {
        val nextYear = Instant.now().atZone(ZoneOffset.UTC).year + 1
        when {
            Instant.ofEpochSecond(this).atZone(ZoneOffset.UTC).year <= nextYear -> TimestampS(this).toTimestampMs()
            Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).year <= nextYear -> TimestampMs(this)
            else -> null
        }
    }
