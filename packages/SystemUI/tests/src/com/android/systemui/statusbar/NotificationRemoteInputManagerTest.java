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

package com.android.systemui.statusbar;

import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.notification.NotifPipelineFlags;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder;
import com.android.systemui.statusbar.notification.collection.render.NotificationVisibilityProvider;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.policy.RemoteInputUriController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import dagger.Lazy;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NotificationRemoteInputManagerTest extends SysuiTestCase {
    private static final String TEST_PACKAGE_NAME = "test";
    private static final int TEST_UID = 0;

    @Mock private NotificationVisibilityProvider mVisibilityProvider;
    @Mock private RemoteInputController.Delegate mDelegate;
    @Mock private NotificationRemoteInputManager.Callback mCallback;
    @Mock private RemoteInputController mController;
    @Mock private SmartReplyController mSmartReplyController;
    @Mock private ExpandableNotificationRow mRow;
    @Mock private StatusBarStateController mStateController;
    @Mock private RemoteInputUriController mRemoteInputUriController;
    @Mock private NotificationClickNotifier mClickNotifier;
    @Mock private NotificationEntryManager mEntryManager;
    @Mock private NotificationLockscreenUserManager mLockscreenUserManager;

    private TestableNotificationRemoteInputManager mRemoteInputManager;
    private NotificationEntry mEntry;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mRemoteInputManager = new TestableNotificationRemoteInputManager(mContext,
                mock(NotifPipelineFlags.class),
                mLockscreenUserManager,
                mSmartReplyController,
                mVisibilityProvider,
                mEntryManager,
                mock(RemoteInputNotificationRebuilder.class),
                () -> Optional.of(mock(CentralSurfaces.class)),
                mStateController,
                Handler.createAsync(Looper.myLooper()),
                mRemoteInputUriController,
                mClickNotifier,
                mock(ActionClickLogger.class),
                mock(DumpManager.class));
        mEntry = new NotificationEntryBuilder()
                .setPkg(TEST_PACKAGE_NAME)
                .setOpPkg(TEST_PACKAGE_NAME)
                .setUid(TEST_UID)
                .setNotification(new Notification())
                .setUser(UserHandle.CURRENT)
                .build();
        mEntry.setRow(mRow);

        mRemoteInputManager.setUpWithPresenterForTest(mCallback, mDelegate, mController);
    }

    @Test
    public void testShouldExtendLifetime_remoteInputActive() {
        when(mController.isRemoteInputActive(mEntry)).thenReturn(true);

        assertTrue(mRemoteInputManager.isRemoteInputActive(mEntry));
    }

    @Test
    public void testShouldExtendLifetime_isSpinning() {
        NotificationRemoteInputManager.FORCE_REMOTE_INPUT_HISTORY = true;
        when(mController.isSpinning(mEntry.getKey())).thenReturn(true);

        assertTrue(mRemoteInputManager.shouldKeepForRemoteInputHistory(mEntry));
    }

    @Test
    public void testShouldExtendLifetime_recentRemoteInput() {
        NotificationRemoteInputManager.FORCE_REMOTE_INPUT_HISTORY = true;
        mEntry.lastRemoteInputSent = SystemClock.elapsedRealtime();

        assertTrue(mRemoteInputManager.shouldKeepForRemoteInputHistory(mEntry));
    }

    @Test
    public void testShouldExtendLifetime_smartReplySending() {
        NotificationRemoteInputManager.FORCE_REMOTE_INPUT_HISTORY = true;
        when(mSmartReplyController.isSendingSmartReply(mEntry.getKey())).thenReturn(true);

        assertTrue(mRemoteInputManager.shouldKeepForSmartReplyHistory(mEntry));
    }

    private class TestableNotificationRemoteInputManager extends NotificationRemoteInputManager {

        TestableNotificationRemoteInputManager(
                Context context,
                NotifPipelineFlags notifPipelineFlags,
                NotificationLockscreenUserManager lockscreenUserManager,
                SmartReplyController smartReplyController,
                NotificationVisibilityProvider visibilityProvider,
                NotificationEntryManager notificationEntryManager,
                RemoteInputNotificationRebuilder rebuilder,
                Lazy<Optional<CentralSurfaces>> centralSurfacesOptionalLazy,
                StatusBarStateController statusBarStateController,
                Handler mainHandler,
                RemoteInputUriController remoteInputUriController,
                NotificationClickNotifier clickNotifier,
                ActionClickLogger actionClickLogger,
                DumpManager dumpManager) {
            super(
                    context,
                    notifPipelineFlags,
                    lockscreenUserManager,
                    smartReplyController,
                    visibilityProvider,
                    notificationEntryManager,
                    centralSurfacesOptionalLazy,
                    statusBarStateController,
                    remoteInputUriController,
                    clickNotifier,
                    actionClickLogger,
                    dumpManager);
        }

        public void setUpWithPresenterForTest(Callback callback,
                RemoteInputController.Delegate delegate,
                RemoteInputController controller) {
            super.setUpWithCallback(callback, delegate);
            mRemoteInputController = controller;
        }

    }
}
