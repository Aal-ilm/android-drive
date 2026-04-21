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

package me.proton.android.drive.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.proton.android.drive.ui.viewevent.SpringSalePromoViewEvent
import me.proton.android.drive.ui.viewmodel.SpringSalePromoViewModel
import me.proton.android.drive.ui.viewstate.SpringSalePromoViewState
import me.proton.core.compose.component.ProtonButton
import me.proton.core.compose.component.protonButtonColors
import me.proton.core.compose.theme.ProtonDimens
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.drive.base.domain.extension.flowOf
import me.proton.core.drive.base.presentation.common.Action
import me.proton.core.drive.base.presentation.component.TopBarActions
import me.proton.core.drive.base.presentation.extension.isLandscape
import me.proton.core.drive.base.presentation.component.TopAppBar as BaseTopAppBar

@Composable
fun SpringSalePromoScreen(
    navigateToSubscription: () -> Unit,
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<SpringSalePromoViewModel>()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle(
        null
    )
    val viewEvent = remember {
        viewModel.viewEvent(
            navigateToSubscription = navigateToSubscription,
            navigateBack = navigateBack,
        )
    }
    val hasState = viewState != null
    Crossfade(hasState) { stateAvailable ->
        if (stateAvailable) {
            val state = viewState!!
            LaunchedEffect(Unit) {
                viewEvent.onPromoShown()
            }
            if (isLandscape) {
                SpringSalePromoScreenLandscape(
                    viewState = state,
                    viewEvent = viewEvent,
                    modifier = modifier,
                )
            } else {
                SpringSalePromoScreen(
                    viewState = state,
                    viewEvent = viewEvent,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
fun SpringSalePromoScreen(
    viewState: SpringSalePromoViewState,
    viewEvent: SpringSalePromoViewEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(viewState.backgroundResId),
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomEnd,
            )
            .statusBarsPadding()
            .padding(horizontal = ProtonDimens.DefaultSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            closeAction = viewState.closeAction,
        )
        Image(
            painter = painterResource(viewState.titleImageResId),
            contentDescription = null,
            modifier = Modifier.padding(top = ProtonDimens.DefaultSpacing),
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            viewState.items.forEach { item ->
                Item(
                    imageResId = item.imageResId,
                    title = item.title,
                    modifier = Modifier.heightIn(min = 48.dp)
                        .padding(bottom = ProtonDimens.LargeSpacing)
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ProtonDimens.DefaultSpacing),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Offer(
                period = viewState.period,
                oneMonthPeriod = viewState.oneMonthPeriod,
                monthlyPrice = viewState.monthlyPrice,
                monthlyPricePeriod = viewState.monthlyPricePeriod,
                yearlyPrice = viewState.yearlyPrice,
                yearlyPricePeriod = viewState.yearlyPricePeriod,
                oneMonthPrice = viewState.oneMonthPrice,
                selected = viewState.selected,
                onToggleOffer = viewEvent.onToggleOffer,
            )
            GetDealButton(
                title = stringResource(viewState.getDealButtonResId),
                onClick = viewEvent.onGetDeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )
            AutoRenewText(
                autoRenewPrice = viewState.autoRenewPrice,
                modifier = Modifier
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
fun SpringSalePromoScreenLandscape(
    viewState: SpringSalePromoViewState,
    viewEvent: SpringSalePromoViewEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(viewState.backgroundLandResId),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopStart,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(viewState.titleImageResId),
                    contentDescription = null,
                    modifier = Modifier.size(width = 375.dp, height = 103.dp)
                )
            }
            TopAppBar(
                closeAction = viewState.closeAction,
                modifier = Modifier.safeDrawingPadding(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RowItems(
                items = viewState.items,
                modifier = Modifier.padding(horizontal = ProtonDimens.LargeSpacing)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = ProtonDimens.SmallSpacing,
                    start = ProtonDimens.DefaultSpacing,
                    end = ProtonDimens.DefaultSpacing,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ProtonDimens.DefaultSpacing)
        ) {
            ClaimOffer(
                title = stringResource(viewState.getDealButtonResId),
                period = viewState.period,
                oneMonthPeriod = viewState.oneMonthPeriod,
                monthlyPrice = viewState.monthlyPrice,
                monthlyPricePeriod = viewState.monthlyPricePeriod,
                yearlyPrice = viewState.yearlyPrice,
                yearlyPricePeriod = viewState.yearlyPricePeriod,
                oneMonthPrice = viewState.oneMonthPrice,
                selected = viewState.selected,
                onClick = viewEvent.onGetDeal,
                onToggleOffer = viewEvent.onToggleOffer,
            )
            AutoRenewText(
                autoRenewPrice = viewState.autoRenewPrice,
            )
        }
    }
}

@Composable
private fun AutoRenewText(
    autoRenewPrice: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val color = if (ProtonTheme.colors.isDark) Color.White else driveCustomBlue
    Text(
        text = autoRenewPrice,
        style = ProtonTheme.typography.body2Medium.copy(
            color = color,
            fontWeight = FontWeight.W400,
        ),
        textAlign = textAlign,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TopAppBar(
    closeAction: Action,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    iconTintColor: Color = ProtonTheme.colors.iconNorm,
    elevation: Dp = 0.dp,
) {
    if (isLandscape) {
        BaseTopAppBar(
            navigationIcon = painterResource((closeAction as Action.Icon).iconResId),
            navigationContentDescription = stringResource((closeAction as Action.Icon).contentDescriptionResId),
            onNavigationIcon = closeAction.onAction,
            title = "",
            backgroundColor = backgroundColor,
            modifier = modifier,
        )
    } else {
        TopAppBar(
            title = {},
            modifier = modifier,
            backgroundColor = backgroundColor,
            elevation = elevation,
            actions = {
                TopBarActions(
                    actionFlow = flowOf { setOf(closeAction) },
                    iconTintColor = iconTintColor,
                )
            }
        )
    }
}

@Composable
private fun Offer(
    period: String,
    oneMonthPeriod: String,
    monthlyPrice: String,
    monthlyPricePeriod: String,
    yearlyPrice: String,
    yearlyPricePeriod: String,
    oneMonthPrice: String,
    selected: SpringSalePromoViewState.PlanPeriod,
    modifier: Modifier = Modifier,
    onToggleOffer: () -> Unit,
) {
    val minHeight = if (isLandscape) 48.dp else 68.dp
    val color = if (ProtonTheme.colors.isDark) Color.White else driveCustomBlue
    val offerColor = driveCustomYellow
    val deselectedColor = ProtonTheme.colors.backgroundSecondary
    val bgColorAlpha = if (ProtonTheme.colors.isDark) 0.1f else 0.4f
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ProtonDimens.SmallSpacing)
    ) {
        val isYearlySelected = selected == SpringSalePromoViewState.PlanPeriod.YEARLY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .border(
                    width = 2.dp,
                    color = if (isYearlySelected) offerColor else deselectedColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(
                    color = Color.White.copy(alpha = bgColorAlpha),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    enabled = !isYearlySelected,
                    onClick = onToggleOffer,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isYearlySelected,
                onClick =  { if (!isYearlySelected) onToggleOffer() },
            )
            Text(
                text = period,
                style = ProtonTheme.typography.body1Medium.copy(color = color),
                modifier = modifier.weight(1f)
            )
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End,
            ) {
                Row {
                    Text(
                        text = yearlyPrice,
                        style = ProtonTheme.typography.body1Bold.copy(color = color)
                    )
                    Text(
                        text = yearlyPricePeriod,
                        style = ProtonTheme.typography.body1Medium.copy(color = color.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Row(modifier = Modifier.padding(top = ProtonDimens.ExtraSmallSpacing)) {
                    Text(
                        text = monthlyPrice,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f))
                    )
                    Text(
                        text = monthlyPricePeriod,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        val isMonthlySelected = selected == SpringSalePromoViewState.PlanPeriod.MONTHLY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .border(
                    width = 2.dp,
                    color = if (isMonthlySelected) color else deselectedColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(
                    color = Color.White.copy(alpha = bgColorAlpha),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    enabled = !isMonthlySelected,
                    onClick = onToggleOffer,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isMonthlySelected,
                onClick =  { if (!isMonthlySelected) onToggleOffer() },
            )
            Text(
                text = oneMonthPeriod,
                style = ProtonTheme.typography.body1Medium.copy(color = color),
                modifier = modifier.weight(1f)
            )
            Box(
                modifier = Modifier,
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier
                ) {
                    Text(
                        text = oneMonthPrice,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f))
                    )
                    Text(
                        text = monthlyPricePeriod,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GetDealButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contained: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ButtonColors = ButtonDefaults.protonButtonColors(loading),
    onClick: () -> Unit,
) {
    ProtonButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        contained = contained,
        interactionSource = interactionSource,
        elevation = ButtonDefaults.elevation(0.dp),
        shape = RoundedCornerShape(100.dp),
        border = null,
        colors = colors,
        contentPadding = ButtonDefaults.ContentPadding,
        content = {
            Text(
                text = title,
                style = ProtonTheme.typography.body1Bold
            )
        },
    )
}

@Composable
fun ClaimOffer(
    title: String,
    period: String,
    oneMonthPeriod: String,
    monthlyPrice: String,
    monthlyPricePeriod: String,
    yearlyPrice: String,
    yearlyPricePeriod: String,
    oneMonthPrice: String,
    selected: SpringSalePromoViewState.PlanPeriod,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleOffer: () -> Unit,
) {
    val color = if (ProtonTheme.colors.isDark) Color.White else driveCustomBlue
    val offerColor = driveCustomYellow
    val deselectedColor = ProtonTheme.colors.backgroundSecondary
    val bgColorAlpha = if (ProtonTheme.colors.isDark) 0.1f else 0.4f
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .height(IntrinsicSize.Min)
            .padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val isMonthlySelected = selected == SpringSalePromoViewState.PlanPeriod.MONTHLY
        Row(
            modifier = Modifier
                .zIndex(0f)
                .offset(x = 48.dp)
                .background(
                    color = Color.White.copy(alpha = bgColorAlpha),
                    shape = RoundedCornerShape(100.dp)
                )
                .border(
                    width = 2.dp,
                    color = if (isMonthlySelected) color else deselectedColor,
                    shape = RoundedCornerShape(100.dp)
                )
                .clip(RoundedCornerShape(100.dp))
                .clickable(
                    enabled = !isMonthlySelected,
                    onClick = onToggleOffer,
                )
                .padding(start = 16.dp, end = 56.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = oneMonthPeriod,
                style = ProtonTheme.typography.body1Medium.copy(color = color),
            )
            Box(
                modifier = Modifier.padding(start = ProtonDimens.DefaultSpacing),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier
                ) {
                    Text(
                        text = oneMonthPrice,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f))
                    )
                    Text(
                        text = monthlyPricePeriod,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        GetDealButton(
            title = title,
            modifier = Modifier
                .zIndex(1f)
                .widthIn(min = 200.dp)
                .fillMaxHeight(),
            onClick = onClick,
        )
        val isYearlySelected = selected == SpringSalePromoViewState.PlanPeriod.YEARLY
        Row(
            modifier = Modifier
                .zIndex(0f)
                .offset(x = (-48).dp)
                .background(
                    color = Color.White.copy(alpha = bgColorAlpha),
                    shape = RoundedCornerShape(100.dp)
                )
                .border(
                    width = 2.dp,
                    color = if (isYearlySelected) offerColor else deselectedColor,
                    shape = RoundedCornerShape(100.dp)
                )
                .clip(RoundedCornerShape(100.dp))
                .clickable(
                    enabled = !isYearlySelected,
                    onClick = onToggleOffer,
                )
                .padding(start = 56.dp, end = 16.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = period,
                style = ProtonTheme.typography.body1Medium.copy(color = color),
            )
            Column(
                modifier = Modifier
                    .padding(start = ProtonDimens.DefaultSpacing, end = 10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End,
            ) {
                Row {
                    Text(
                        text = yearlyPrice,
                        style = ProtonTheme.typography.body1Bold.copy(color = color)
                    )
                    Text(
                        text = yearlyPricePeriod,
                        style = ProtonTheme.typography.body1Medium.copy(color = color.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Row(modifier = Modifier.padding(top = ProtonDimens.ExtraSmallSpacing)) {
                    Text(
                        text = monthlyPrice,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f))
                    )
                    Text(
                        text = monthlyPricePeriod,
                        style = ProtonTheme.typography.body2Medium.copy(color = color.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Item(
    imageResId: Int,
    title: String,
    modifier: Modifier = Modifier
) {
    val color = if (ProtonTheme.colors.isDark) Color.White else driveCustomBlue
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ProtonDimens.DefaultSpacing)
    ) {
        Image(
            painter = painterResource(imageResId),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = ProtonTheme.typography.subheadline.copy(
                color = color,
                fontWeight = FontWeight.Light,
            ),
        )
    }
}

@Composable
fun RowItems(
    items: Set<SpringSalePromoViewState.Item>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            CardItem(
                imageResId = item.imageResId,
                title = item.title,
                modifier = Modifier
                    .padding(horizontal = ProtonDimens.DefaultSpacing)
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun CardItem(
    imageResId: Int,
    title: String,
    modifier: Modifier = Modifier
) {
    val color = if (ProtonTheme.colors.isDark) Color.White else driveCustomBlue
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(imageResId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = title,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            style = ProtonTheme.typography.body1Regular.copy(color = color),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ProtonDimens.SmallSpacing),
        )
    }
}

private val driveCustomBlue = Color(0xFF372580)
private val driveCustomYellow = Color(0xFFFCD060)
