/*
 * Copyright (c) 2026 Proton AG.
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

package me.proton.android.drive.usecase.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.android.drive.extension.deepLinkBaseUrl
import me.proton.android.drive.receiver.NotificationBroadcastReceiver
import me.proton.android.drive.receiver.NotificationBroadcastReceiver.Companion.ACTION_CANCEL_ALL_DOWNLOADS
import me.proton.android.drive.receiver.NotificationBroadcastReceiver.Companion.EXTRA_NOTIFICATION_ID
import me.proton.android.drive.ui.navigation.Screen
import me.proton.core.drive.announce.event.domain.entity.Event
import me.proton.core.drive.notification.domain.entity.NotificationId
import me.proton.core.util.kotlin.serialize
import javax.inject.Inject
import me.proton.core.drive.i18n.R as I18N

class DownloadFileProgressNotificationBuilder @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val commonBuilder: CommonNotificationBuilder,
    private val contentIntent: CreateContentPendingIntent,
) {
    operator fun invoke(
        notificationId: NotificationId.User,
        events: List<Event.DownloadFileProgress>,
    ) = events.last().let { event ->
        commonBuilder(notificationId, event)
            .setContentText(
                appContext.resources.getQuantityString(
                    I18N.plurals.notification_content_text_download_downloading,
                    event.downloadingCount,
                    event.downloadingCount,
                )
            )
            .setProgress(100, (event.progress.value * 100).toInt(), false)
            .setSilent(true)
            .setContentIntent(
                contentIntent(
                    notificationId = notificationId,
                    uri = "${appContext.deepLinkBaseUrl}/${Screen.Files(notificationId.channel.userId)}".toUri(),
                )
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    appContext.getString(
                        if (event.downloadingCount > 1) {
                            I18N.string.common_cancel_all_action
                        } else {
                            I18N.string.common_cancel_action
                        }
                    ),
                    cancelAllDownloadsIntent(notificationId),
                ).build()
            )
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun cancelAllDownloadsIntent(
        notificationId: NotificationId,
        requestCode: Int = 8888,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, NotificationBroadcastReceiver::class.java).apply {
                action = ACTION_CANCEL_ALL_DOWNLOADS
                putExtra(EXTRA_NOTIFICATION_ID, notificationId.serialize())
            },
            PendingIntent.FLAG_IMMUTABLE
        )
}
