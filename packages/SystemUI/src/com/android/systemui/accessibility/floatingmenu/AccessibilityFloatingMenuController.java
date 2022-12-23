/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.accessibility.floatingmenu;

import static android.provider.Settings.Secure.ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.systemui.flags.Flags.A11Y_FLOATING_MENU_FLING_SPRING_ANIMATIONS;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.MainThread;

import com.android.internal.annotations.VisibleForTesting;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver;
import com.android.systemui.accessibility.AccessibilityButtonModeObserver.AccessibilityButtonMode;
import com.android.systemui.accessibility.AccessibilityButtonTargetsObserver;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.flags.FeatureFlags;

import javax.inject.Inject;

/** A controller to handle the lifecycle of accessibility floating menu. */
@MainThread
@SysUISingleton
public class AccessibilityFloatingMenuController implements
        AccessibilityButtonModeObserver.ModeChangedListener,
        AccessibilityButtonTargetsObserver.TargetsChangedListener {

    private final AccessibilityButtonModeObserver mAccessibilityButtonModeObserver;
    private final AccessibilityButtonTargetsObserver mAccessibilityButtonTargetsObserver;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    private Context mContext;
    private final WindowManager mWindowManager;
    private final DisplayManager mDisplayManager;
    private final AccessibilityManager mAccessibilityManager;
    private final FeatureFlags mFeatureFlags;
    @VisibleForTesting
    IAccessibilityFloatingMenu mFloatingMenu;
    private int mBtnMode;
    private String mBtnTargets;
    private boolean mIsKeyguardVisible;

    @VisibleForTesting
    final KeyguardUpdateMonitorCallback mKeyguardCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onUserUnlocked() {
            handleFloatingMenuVisibility(mIsKeyguardVisible, mBtnMode, mBtnTargets);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean visible) {
            mIsKeyguardVisible = visible;
            handleFloatingMenuVisibility(mIsKeyguardVisible, mBtnMode, mBtnTargets);
        }

        @Override
        public void onUserSwitching(int userId) {
            destroyFloatingMenu();
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            mContext = mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
            mBtnMode = mAccessibilityButtonModeObserver.getCurrentAccessibilityButtonMode();
            mBtnTargets =
                    mAccessibilityButtonTargetsObserver.getCurrentAccessibilityButtonTargets();
            handleFloatingMenuVisibility(mIsKeyguardVisible, mBtnMode, mBtnTargets);
        }
    };

    @Inject
    public AccessibilityFloatingMenuController(Context context,
            WindowManager windowManager,
            DisplayManager displayManager,
            AccessibilityManager accessibilityManager,
            AccessibilityButtonTargetsObserver accessibilityButtonTargetsObserver,
            AccessibilityButtonModeObserver accessibilityButtonModeObserver,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            FeatureFlags featureFlags) {
        mContext = context;
        mWindowManager = windowManager;
        mDisplayManager = displayManager;
        mAccessibilityManager = accessibilityManager;
        mAccessibilityButtonTargetsObserver = accessibilityButtonTargetsObserver;
        mAccessibilityButtonModeObserver = accessibilityButtonModeObserver;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mFeatureFlags = featureFlags;

        mIsKeyguardVisible = false;
    }

    /**
     * Handles visibility of the accessibility floating menu when accessibility button mode changes.
     *
     * @param mode Current accessibility button mode.
     */
    @Override
    public void onAccessibilityButtonModeChanged(@AccessibilityButtonMode int mode) {
        mBtnMode = mode;
        handleFloatingMenuVisibility(mIsKeyguardVisible, mBtnMode, mBtnTargets);
    }

    /**
     * Handles visibility of the accessibility floating menu when accessibility button targets
     * changes.
     * List should come from {@link android.provider.Settings.Secure#ACCESSIBILITY_BUTTON_TARGETS}.
     * @param targets Current accessibility button list.
     */
    @Override
    public void onAccessibilityButtonTargetsChanged(String targets) {
        mBtnTargets = targets;
        handleFloatingMenuVisibility(mIsKeyguardVisible, mBtnMode, mBtnTargets);
    }

    /** Initializes the AccessibilityFloatingMenuController configurations. */
    public void init() {
        mBtnMode = mAccessibilityButtonModeObserver.getCurrentAccessibilityButtonMode();
        mBtnTargets = mAccessibilityButtonTargetsObserver.getCurrentAccessibilityButtonTargets();
        registerContentObservers();
    }

    private void registerContentObservers() {
        mAccessibilityButtonModeObserver.addListener(this);
        mAccessibilityButtonTargetsObserver.addListener(this);
        mKeyguardUpdateMonitor.registerCallback(mKeyguardCallback);
    }

    /**
     * Handles the accessibility floating menu visibility with the given values.
     *
     * @param keyguardVisible the keyguard visibility status. Not show the
     *                        {@link AccessibilityFloatingMenu} when keyguard appears.
     * @param mode accessibility button mode {@link AccessibilityButtonMode}
     * @param targets accessibility button list; it should comes from
     *                {@link android.provider.Settings.Secure#ACCESSIBILITY_BUTTON_TARGETS}.
     */
    private void handleFloatingMenuVisibility(boolean keyguardVisible,
            @AccessibilityButtonMode int mode, String targets) {
        if (keyguardVisible) {
            destroyFloatingMenu();
            return;
        }

        if (shouldShowFloatingMenu(mode, targets)) {
            showFloatingMenu();
        } else {
            destroyFloatingMenu();
        }
    }

    private boolean shouldShowFloatingMenu(@AccessibilityButtonMode int mode, String targets) {
        return mode == ACCESSIBILITY_BUTTON_MODE_FLOATING_MENU && !TextUtils.isEmpty(targets);
    }

    private void showFloatingMenu() {
        if (mFloatingMenu == null) {
            if (mFeatureFlags.isEnabled(A11Y_FLOATING_MENU_FLING_SPRING_ANIMATIONS)) {
                final Display defaultDisplay = mDisplayManager.getDisplay(DEFAULT_DISPLAY);
                mFloatingMenu = new MenuViewLayerController(
                        mContext.createWindowContext(defaultDisplay,
                                TYPE_NAVIGATION_BAR_PANEL, /* options= */ null), mWindowManager,
                        mAccessibilityManager);
            } else {
                mFloatingMenu = new AccessibilityFloatingMenu(mContext);
            }
        }

        mFloatingMenu.show();
    }

    private void destroyFloatingMenu() {
        if (mFloatingMenu == null) {
            return;
        }

        mFloatingMenu.hide();
        mFloatingMenu = null;
    }
}
