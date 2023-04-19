/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.telephony.TelephonyManager
import com.android.settingslib.AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH
import com.android.settingslib.AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH_NONE
import com.android.settingslib.graph.SignalDrawable
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.R
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.AirplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.data.model.MobileIconCustomizationMode
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.ui.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Common interface for all of the location-based mobile icon view models. */
interface MobileIconViewModelCommon {
    val subscriptionId: Int
    /** True if this view should be visible at all. */
    val isVisible: StateFlow<Boolean>
    val icon: Flow<SignalIconModel>
    val contentDescription: Flow<ContentDescription>
    val roaming: Flow<Boolean>
    /** The RAT icon (LTE, 3G, 5G, etc) to be displayed. Null if we shouldn't show anything */
    val networkTypeIcon: Flow<Icon.Resource?>
    val activityInVisible: Flow<Boolean>
    val activityOutVisible: Flow<Boolean>
    val activityContainerVisible: Flow<Boolean>
    val volteId: Flow<Int>
}

/**
 * View model for the state of a single mobile icon. Each [MobileIconViewModel] will keep watch over
 * a single line of service via [MobileIconInteractor] and update the UI based on that
 * subscription's information.
 *
 * There will be exactly one [MobileIconViewModel] per filtered subscription offered from
 * [MobileIconsInteractor.filteredSubscriptions].
 *
 * For the sake of keeping log spam in check, every flow funding the [MobileIconViewModelCommon]
 * interface is implemented as a [StateFlow]. This ensures that each location-based mobile icon view
 * model gets the exact same information, as well as allows us to log that unified state only once
 * per icon.
 */
@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class)
class MobileIconViewModel
constructor(
    override val subscriptionId: Int,
    iconInteractor: MobileIconInteractor,
    airplaneModeInteractor: AirplaneModeInteractor,
    constants: ConnectivityConstants,
    scope: CoroutineScope,
) : MobileIconViewModelCommon {
    /** Whether or not to show the error state of [SignalDrawable] */
    private val showExclamationMark: Flow<Boolean> =
        combine(
            iconInteractor.isDefaultDataEnabled,
            iconInteractor.hideNoInternetState
        ){ isDefaultDataEnabled, hideNoInternetState ->
            !isDefaultDataEnabled && !hideNoInternetState
        }

    override val isVisible: StateFlow<Boolean> =
        if (!constants.hasDataCapabilities) {
                flowOf(false)
            } else {
                combine(
                    airplaneModeInteractor.isAirplaneMode,
                    iconInteractor.isForceHidden,
                ) { isAirplaneMode, isForceHidden ->
                    !isAirplaneMode && !isForceHidden
                }
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "",
                columnName = "visible",
                initialValue = false,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    override val icon: Flow<SignalIconModel> = run {
        val initial = SignalIconModel.createEmptyState(iconInteractor.numberOfLevels.value)
        combine(
                iconInteractor.level,
                iconInteractor.numberOfLevels,
                showExclamationMark,
                iconInteractor.isInService,
            ) { level, numberOfLevels, showExclamationMark, isInService ->
                if (!isInService) {
                    SignalIconModel.createEmptyState(numberOfLevels)
                } else {
                    SignalIconModel(level, numberOfLevels, showExclamationMark)
                }
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "icon",
                initialValue = initial,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), initial)
    }

    override val contentDescription: Flow<ContentDescription> = run {
        val initial = ContentDescription.Resource(PHONE_SIGNAL_STRENGTH_NONE)
        combine(
                iconInteractor.level,
                iconInteractor.isInService,
            ) { level, isInService ->
                val resId =
                    when {
                        isInService -> PHONE_SIGNAL_STRENGTH[level]
                        else -> PHONE_SIGNAL_STRENGTH_NONE
                    }
                ContentDescription.Resource(resId)
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), initial)
    }

    private val showNetworkTypeIcon: Flow<Boolean> =
        combine(
                iconInteractor.isDataConnected,
                iconInteractor.isDataEnabled,
                iconInteractor.isDefaultConnectionFailed,
                iconInteractor.alwaysShowDataRatIcon,
                iconInteractor.isConnected,
            ) { dataConnected, dataEnabled, failedConnection, alwaysShow, connected ->
                alwaysShow || (dataConnected && dataEnabled && !failedConnection && connected)
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "",
                columnName = "showNetworkTypeIcon",
                initialValue = false,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    override val networkTypeIcon: Flow<Icon.Resource?> =
        combine(
                iconInteractor.networkTypeIconGroup,
                showNetworkTypeIcon,
                iconInteractor.networkTypeIconCustomization,
                iconInteractor.voWifiAvailable,
            ) { networkTypeIconGroup, shouldShow, networkTypeIconCustomization, voWifiAvailable ->
                val desc =
                    if (networkTypeIconGroup.dataContentDescription != 0)
                        ContentDescription.Resource(networkTypeIconGroup.dataContentDescription)
                    else null
                val icon =
                    if (voWifiAvailable) {
                        Icon.Resource(TelephonyIcons.VOWIFI.dataType, desc)
                    } else {
                        Icon.Resource(networkTypeIconGroup.dataType, desc)
                    }
                return@combine when {
                    networkTypeIconCustomization.isRatCustomization -> {
                        if (shouldShowNetworkTypeIcon(networkTypeIconCustomization)) {
                            icon
                        } else {
                            null
                        }
                    }
                    !shouldShow -> null
                    else -> icon
                }
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    override val roaming: StateFlow<Boolean> =
        iconInteractor.isRoaming
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "",
                columnName = "roaming",
                initialValue = false,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)


    override val volteId =
        combine (
                iconInteractor.imsInfo,
                iconInteractor.showVolteIcon,
        ) { imsInfo, showVolteIcon ->
             if (!showVolteIcon) {
                return@combine 0
            }
            val voiceNetworkType = imsInfo.voiceNetworkType
            val netWorkType = imsInfo.originNetworkType
            if ((imsInfo.voiceCapable || imsInfo.videoCapable) && imsInfo.imsRegistered) {
                return@combine R.drawable.ic_volte
            } else if ((netWorkType == TelephonyManager.NETWORK_TYPE_LTE
                        || netWorkType == TelephonyManager.NETWORK_TYPE_LTE_CA)
                && voiceNetworkType  == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                return@combine R.drawable.ic_volte_no_voice
            } else {
                return@combine 0
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(), 0)

    private val activity: Flow<DataActivityModel?> =
        if (!constants.shouldShowActivityConfig) {
            flowOf(null)
        } else {
            iconInteractor.activity
        }

    override val activityInVisible: Flow<Boolean> =
        activity
            .map { it?.hasActivityIn ?: false }
            .distinctUntilChanged()
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "",
                columnName = "activityInVisible",
                initialValue = false,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    override val activityOutVisible: Flow<Boolean> =
        activity
            .map { it?.hasActivityOut ?: false }
            .distinctUntilChanged()
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "",
                columnName = "activityOutVisible",
                initialValue = false,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    override val activityContainerVisible: Flow<Boolean> =
        activity
            .map { it != null && (it.hasActivityIn || it.hasActivityOut) }
            .distinctUntilChanged()
            .logDiffsForTable(
                iconInteractor.tableLogBuffer,
                columnPrefix = "",
                columnName = "activityContainerVisible",
                initialValue = false,
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private fun shouldShowNetworkTypeIcon(mode: MobileIconCustomizationMode): Boolean {
        return (mode.alwaysShowNetworkTypeIcon
            || mode.ddsRatIconEnhancementEnabled && mode.isDefaultDataSub
            || mode.nonDdsRatIconEnhancementEnabled
                && mode.mobileDataEnabled && (mode.dataRoamingEnabled || !mode.isRoaming))
    }
}
