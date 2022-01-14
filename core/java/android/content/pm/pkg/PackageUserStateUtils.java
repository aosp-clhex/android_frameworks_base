/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.content.pm.pkg;

import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;

import android.annotation.NonNull;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.parsing.ParsingPackageRead;
import android.content.pm.parsing.component.ParsedMainComponent;
import android.os.Debug;
import android.util.DebugUtils;
import android.util.Slog;

/** @hide */
public class PackageUserStateUtils {

    private static final boolean DEBUG = false;
    private static final String TAG = "PackageUserStateUtils";

    public static boolean isMatch(@NonNull FrameworkPackageUserState state, ComponentInfo componentInfo,
            int flags) {
        return isMatch(state, componentInfo.applicationInfo.isSystemApp(),
                componentInfo.applicationInfo.enabled, componentInfo.enabled,
                componentInfo.directBootAware, componentInfo.name, flags);
    }

    public static boolean isMatch(@NonNull FrameworkPackageUserState state, boolean isSystem,
            boolean isPackageEnabled, ParsedMainComponent component, int flags) {
        return isMatch(state, isSystem, isPackageEnabled, component.isEnabled(),
                component.isDirectBootAware(), component.getName(), flags);
    }

    /**
     * Test if the given component is considered installed, enabled and a match for the given
     * flags.
     *
     * <p>
     * Expects at least one of {@link PackageManager#MATCH_DIRECT_BOOT_AWARE} and {@link
     * PackageManager#MATCH_DIRECT_BOOT_UNAWARE} are specified in {@code flags}.
     * </p>
     */
    public static boolean isMatch(@NonNull FrameworkPackageUserState state, boolean isSystem,
            boolean isPackageEnabled, boolean isComponentEnabled,
            boolean isComponentDirectBootAware, String componentName, int flags) {
        final boolean matchUninstalled = (flags & PackageManager.MATCH_KNOWN_PACKAGES) != 0;
        if (!isAvailable(state, flags) && !(isSystem && matchUninstalled)) {
            return reportIfDebug(false, flags);
        }

        if (!isEnabled(state, isPackageEnabled, isComponentEnabled, componentName, flags)) {
            return reportIfDebug(false, flags);
        }

        if ((flags & PackageManager.MATCH_SYSTEM_ONLY) != 0) {
            if (!isSystem) {
                return reportIfDebug(false, flags);
            }
        }

        final boolean matchesUnaware = ((flags & PackageManager.MATCH_DIRECT_BOOT_UNAWARE) != 0)
                && !isComponentDirectBootAware;
        final boolean matchesAware = ((flags & PackageManager.MATCH_DIRECT_BOOT_AWARE) != 0)
                && isComponentDirectBootAware;
        return reportIfDebug(matchesUnaware || matchesAware, flags);
    }

    public static boolean isAvailable(@NonNull FrameworkPackageUserState state, int flags) {
        // True if it is installed for this user and it is not hidden. If it is hidden,
        // still return true if the caller requested MATCH_UNINSTALLED_PACKAGES
        final boolean matchAnyUser = (flags & PackageManager.MATCH_ANY_USER) != 0;
        final boolean matchUninstalled = (flags & PackageManager.MATCH_UNINSTALLED_PACKAGES) != 0;
        return matchAnyUser
                || (state.isInstalled()
                && (!state.isHidden() || matchUninstalled));
    }

    public static boolean reportIfDebug(boolean result, int flags) {
        if (DEBUG && !result) {
            Slog.i(TAG, "No match!; flags: "
                    + DebugUtils.flagsToString(PackageManager.class, "MATCH_", flags) + " "
                    + Debug.getCaller());
        }
        return result;
    }

    public static boolean isEnabled(@NonNull FrameworkPackageUserState state, ComponentInfo componentInfo,
            int flags) {
        return isEnabled(state, componentInfo.applicationInfo.enabled, componentInfo.enabled,
                componentInfo.name, flags);
    }

    public static boolean isEnabled(@NonNull FrameworkPackageUserState state, boolean isPackageEnabled,
            ParsedMainComponent parsedComponent, int flags) {
        return isEnabled(state, isPackageEnabled, parsedComponent.isEnabled(),
                parsedComponent.getName(), flags);
    }

    /**
     * Test if the given component is considered enabled.
     */
    public static boolean isEnabled(@NonNull FrameworkPackageUserState state, boolean isPackageEnabled,
            boolean isComponentEnabled, String componentName, int flags) {
        if ((flags & MATCH_DISABLED_COMPONENTS) != 0) {
            return true;
        }

        // First check if the overall package is disabled; if the package is
        // enabled then fall through to check specific component
        switch (state.getEnabledState()) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                if ((flags & MATCH_DISABLED_UNTIL_USED_COMPONENTS) == 0) {
                    return false;
                }
                // fallthrough
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                if (!isPackageEnabled) {
                    return false;
                }
                // fallthrough
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                break;
        }

        // Check if component has explicit state before falling through to
        // the manifest default
        if (state.isComponentEnabled(componentName)) {
            return true;
        } else if (state.isComponentDisabled(componentName)) {
            return false;
        }

        return isComponentEnabled;
    }

    public static boolean isPackageEnabled(@NonNull FrameworkPackageUserState state,
            @NonNull ParsingPackageRead pkg) {
        switch (state.getEnabledState()) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return false;
            default:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return pkg.isEnabled();
        }
    }
}
