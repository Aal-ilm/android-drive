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

package me.proton.android.drive.initializer

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.startup.Initializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.android.drive.extension.log
import me.proton.android.drive.usecase.GetDynamicShortcuts
import me.proton.android.drive.usecase.UpdateDynamicShortcuts
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountReady
import me.proton.core.accountmanager.presentation.onAccountRemoved
import me.proton.core.drive.base.domain.log.LogTag
import me.proton.core.presentation.app.AppLifecycleProvider

class ShortcutInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ShortcutInitializerEntryPoint::class.java,
        ).run {
            accountManager.observe(
                lifecycle = appLifecycleProvider.lifecycle,
                minActiveState = Lifecycle.State.CREATED,
            )
                .onAccountReady { account ->
                    val userId = account.userId
                    getDynamicShortcuts(userId)
                        .onEach { dynamicShortcuts ->
                            updateDynamicShortcuts(dynamicShortcuts)
                                .onFailure { error ->
                                    error.log(
                                        tag = LogTag.DEFAULT,
                                        message = "Failed to update dynamic shortcuts",
                                    )
                                }
                        }
                        .launchIn(appLifecycleProvider.lifecycle.coroutineScope)
                }
                .onAccountRemoved {
                    updateDynamicShortcuts(emptyList())
                        .onFailure { error ->
                            error.log(
                                tag = LogTag.DEFAULT,
                                message = "Failed to clear dynamic shortcuts",
                            )
                        }
                }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(
        AccountStateHandlerInitializer::class.java,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ShortcutInitializerEntryPoint {
        val accountManager: AccountManager
        val appLifecycleProvider: AppLifecycleProvider
        val updateDynamicShortcuts: UpdateDynamicShortcuts
        val getDynamicShortcuts: GetDynamicShortcuts
    }
}
