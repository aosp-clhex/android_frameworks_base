/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.backup;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.backup.BackupManager;
import android.app.backup.IBackupManagerMonitor;
import android.app.backup.IBackupObserver;
import android.app.backup.IFullBackupRestoreObserver;
import android.app.backup.ISelectBackupTransportCallback;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.backup.utils.RandomAccessFileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class TrampolineTest {
    private static final String PACKAGE_NAME = "some.package.name";
    private static final String TRANSPORT_NAME = "some.transport.name";
    private static final String CURRENT_PASSWORD = "current_password";
    private static final String NEW_PASSWORD = "new_password";
    private static final String ENCRYPTION_PASSWORD = "encryption_password";
    private static final CharSequence DATA_MANAGEMENT_LABEL = "data_management_label";
    private static final String DESTINATION_STRING = "destination_string";
    private static final String[] PACKAGE_NAMES =
            new String[]{"some.package.name._1", "some.package.name._2"};
    private static final String[] TRANSPORTS =
            new String[]{"some.transport.name._1", "some.transport.name._2"};
    private static final ComponentName TRANSPORT_COMPONENT_NAME = new ComponentName("package",
            "class");
    private static final ComponentName[] TRANSPORT_COMPONENTS = new ComponentName[]{
            new ComponentName("package1", "class1"),
            new ComponentName("package2", "class2")
    };
    private static final int NON_USER_SYSTEM = UserHandle.USER_SYSTEM + 1;
    private static final int UNSTARTED_NON_USER_SYSTEM = UserHandle.USER_SYSTEM + 2;

    @UserIdInt
    private int mUserId;
    @Mock
    private BackupManagerService mBackupManagerServiceMock;
    @Mock
    private UserBackupManagerService mUserBackupManagerService;
    @Mock
    private Context mContextMock;
    @Mock
    private IBinder mAgentMock;
    @Mock
    private ParcelFileDescriptor mParcelFileDescriptorMock;
    @Mock
    private IFullBackupRestoreObserver mFullBackupRestoreObserverMock;
    @Mock
    private IBackupObserver mBackupObserverMock;
    @Mock
    private IBackupManagerMonitor mBackupManagerMonitorMock;
    @Mock
    private PrintWriter mPrintWriterMock;
    @Mock
    private UserManager mUserManagerMock;
    @Mock
    private UserInfo mUserInfoMock;

    private FileDescriptor mFileDescriptorStub = new FileDescriptor();

    private TrampolineTestable mTrampoline;
    private File mTestDir;
    private File mSuppressFile;
    private SparseArray<UserBackupManagerService> mUserServices;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUserId = UserHandle.USER_SYSTEM;

        mUserServices = new SparseArray<>();
        mUserServices.append(UserHandle.USER_SYSTEM, mUserBackupManagerService);
        mUserServices.append(NON_USER_SYSTEM, mUserBackupManagerService);

        when(mUserManagerMock.getUserInfo(UserHandle.USER_SYSTEM)).thenReturn(mUserInfoMock);
        when(mUserManagerMock.getUserInfo(NON_USER_SYSTEM)).thenReturn(mUserInfoMock);
        when(mUserManagerMock.getUserInfo(UNSTARTED_NON_USER_SYSTEM)).thenReturn(mUserInfoMock);

        TrampolineTestable.sBackupManagerServiceMock = mBackupManagerServiceMock;
        TrampolineTestable.sCallingUserId = UserHandle.USER_SYSTEM;
        TrampolineTestable.sCallingUid = Process.SYSTEM_UID;
        TrampolineTestable.sBackupDisabled = false;
        TrampolineTestable.sUserManagerMock = mUserManagerMock;

        mTestDir = InstrumentationRegistry.getContext().getFilesDir();
        mTestDir.mkdirs();

        mSuppressFile = new File(mTestDir, "suppress");
        TrampolineTestable.sSuppressFile = mSuppressFile;

        setUpStateFilesForNonSystemUser(NON_USER_SYSTEM);
        setUpStateFilesForNonSystemUser(UNSTARTED_NON_USER_SYSTEM);

        when(mContextMock.getSystemService(Context.JOB_SCHEDULER_SERVICE))
                .thenReturn(mock(JobScheduler.class));
        mTrampoline = new TrampolineTestable(mContextMock, mUserServices);
    }

    private void setUpStateFilesForNonSystemUser(int userId) {
        File activatedFile = new File(mTestDir, "activate-" + userId);
        TrampolineTestable.sActivatedFiles.append(userId, activatedFile);
        File rememberActivatedFile = new File(mTestDir, "rem-activate-" + userId);
        TrampolineTestable.sRememberActivatedFiles.append(userId, rememberActivatedFile);
    }

    @After
    public void tearDown() throws Exception {
        mSuppressFile.delete();
        deleteFiles(TrampolineTestable.sActivatedFiles);
        deleteFiles(TrampolineTestable.sRememberActivatedFiles);
    }

    private void deleteFiles(SparseArray<File> files) {
        int numFiles = files.size();
        for (int i = 0; i < numFiles; i++) {
            files.valueAt(i).delete();
        }
    }

    @Test
    public void testIsBackupServiceActive_whenBackupsNotDisabledAndSuppressFileDoesNotExist() {
        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testOnUnlockUser_forNonSystemUserWhenBackupsDisabled_doesNotStartUser() {
        TrampolineTestable.sBackupDisabled = true;
        TrampolineTestable trampoline = new TrampolineTestable(mContextMock, new SparseArray<>());
        ConditionVariable unlocked = new ConditionVariable(false);

        trampoline.onUnlockUser(NON_USER_SYSTEM);

        trampoline.getBackupHandler().post(unlocked::open);
        unlocked.block();
        assertNull(trampoline.getUserService(NON_USER_SYSTEM));
    }

    @Test
    public void testOnUnlockUser_forSystemUserWhenBackupsDisabled_doesNotStartUser() {
        TrampolineTestable.sBackupDisabled = true;
        TrampolineTestable trampoline = new TrampolineTestable(mContextMock, new SparseArray<>());
        ConditionVariable unlocked = new ConditionVariable(false);

        trampoline.onUnlockUser(UserHandle.USER_SYSTEM);

        trampoline.getBackupHandler().post(unlocked::open);
        unlocked.block();
        assertNull(trampoline.getUserService(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testOnUnlockUser_whenBackupNotActivated_doesNotStartUser() {
        TrampolineTestable.sBackupDisabled = false;
        TrampolineTestable trampoline = new TrampolineTestable(mContextMock, new SparseArray<>());
        trampoline.setBackupServiceActive(NON_USER_SYSTEM, false);
        ConditionVariable unlocked = new ConditionVariable(false);

        trampoline.onUnlockUser(NON_USER_SYSTEM);

        trampoline.getBackupHandler().post(unlocked::open);
        unlocked.block();
        assertNull(trampoline.getUserService(NON_USER_SYSTEM));
    }

    @Test
    public void testIsBackupServiceActive_forSystemUserWhenBackupDisabled_returnsTrue()
            throws Exception {
        TrampolineTestable.sBackupDisabled = true;
        Trampoline trampoline = new TrampolineTestable(mContextMock, mUserServices);
        trampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        assertFalse(trampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testIsBackupServiceActive_forNonSystemUserWhenBackupDisabled_returnsTrue()
            throws Exception {
        TrampolineTestable.sBackupDisabled = true;
        Trampoline trampoline = new TrampolineTestable(mContextMock, mUserServices);
        trampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertFalse(trampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void isBackupServiceActive_forSystemUser_returnsTrueWhenActivated() throws Exception {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void isBackupServiceActive_forSystemUser_returnsFalseWhenDeactivated() throws Exception {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, false);

        assertFalse(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void isBackupServiceActive_forNonSystemUser_returnsFalseWhenSystemUserDeactivated()
            throws Exception {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, false);
        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertFalse(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void isBackupServiceActive_forNonSystemUser_returnsFalseWhenNonSystemUserDeactivated()
            throws Exception {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);
        // Don't activate non-system user.

        assertFalse(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void
            isBackupServiceActive_forNonSystemUser_returnsTrueWhenSystemAndNonSystemUserActivated()
                throws Exception {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);
        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void
            isBackupServiceActive_forUnstartedNonSystemUser_returnsTrueWhenSystemAndUserActivated()
            throws Exception {
        mTrampoline.setBackupServiceActive(UNSTARTED_NON_USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(UNSTARTED_NON_USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_forSystemUserAndCallerSystemUid_serviceCreated() {
        TrampolineTestable.sCallingUid = Process.SYSTEM_UID;

        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_forSystemUserAndCallerRootUid_serviceCreated() {
        TrampolineTestable.sCallingUid = Process.ROOT_UID;

        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_forSystemUserAndCallerNonRootNonSystem_throws() {
        TrampolineTestable.sCallingUid = Process.FIRST_APPLICATION_UID;

        try {
            mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);
            fail();
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void setBackupServiceActive_forManagedProfileAndCallerSystemUid_serviceCreated() {
        when(mUserInfoMock.isManagedProfile()).thenReturn(true);
        TrampolineTestable.sCallingUid = Process.SYSTEM_UID;

        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_forManagedProfileAndCallerRootUid_serviceCreated() {
        when(mUserInfoMock.isManagedProfile()).thenReturn(true);
        TrampolineTestable.sCallingUid = Process.ROOT_UID;

        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_forManagedProfileAndCallerNonRootNonSystem_throws() {
        when(mUserInfoMock.isManagedProfile()).thenReturn(true);
        TrampolineTestable.sCallingUid = Process.FIRST_APPLICATION_UID;

        try {
            mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);
            fail();
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void setBackupServiceActive_forNonSystemUserAndCallerWithoutBackupPermission_throws() {
        doThrow(new SecurityException())
                .when(mContextMock)
                .enforceCallingOrSelfPermission(eq(Manifest.permission.BACKUP), anyString());

        try {
            mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);
            fail();
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void setBackupServiceActive_forNonSystemUserAndCallerWithoutUserPermission_throws() {
        doThrow(new SecurityException())
                .when(mContextMock)
                .enforceCallingOrSelfPermission(
                        eq(Manifest.permission.INTERACT_ACROSS_USERS_FULL), anyString());

        try {
            mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);
            fail();
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void setBackupServiceActive_backupDisabled_ignored() {
        TrampolineTestable.sBackupDisabled = true;
        TrampolineTestable trampoline = new TrampolineTestable(mContextMock, mUserServices);

        trampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        assertFalse(trampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_alreadyActive_ignored() {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);
        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));

        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);
        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_makeNonActive_alreadyNonActive_ignored() {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, false);
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, false);

        assertFalse(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_makeActive_serviceCreatedAndSuppressFileDeleted() {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_makeNonActive_serviceDeletedAndSuppressFileCreated()
            throws IOException {
        assertTrue(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));

        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, false);

        assertFalse(mTrampoline.isBackupServiceActive(UserHandle.USER_SYSTEM));
    }

    @Test
    public void setBackupActive_nonSystemUser_disabledForSystemUser_ignored() {
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, false);
        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertFalse(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
    }

    @Test
    public void setBackupServiceActive_forOneNonSystemUser_doesNotActivateForAllNonSystemUsers() {
        int otherUser = NON_USER_SYSTEM + 1;
        File activateFile = new File(mTestDir, "activate-" + otherUser);
        TrampolineTestable.sActivatedFiles.append(otherUser, activateFile);
        mTrampoline.setBackupServiceActive(UserHandle.USER_SYSTEM, true);

        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertTrue(mTrampoline.isBackupServiceActive(NON_USER_SYSTEM));
        assertFalse(mTrampoline.isBackupServiceActive(otherUser));
        activateFile.delete();
    }

    @Test
    public void setBackupServiceActive_forNonSystemUser_remembersActivated() {

        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);

        assertTrue(RandomAccessFileUtils.readBoolean(
                TrampolineTestable.sRememberActivatedFiles.get(NON_USER_SYSTEM), false));
    }

    @Test
    public void setBackupServiceActiveFalse_forNonSystemUser_remembersActivated() {

        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, false);

        assertFalse(RandomAccessFileUtils.readBoolean(
                TrampolineTestable.sRememberActivatedFiles.get(NON_USER_SYSTEM), true));
    }

    @Test
    public void setBackupServiceActiveTwice_forNonSystemUser_remembersLastActivated() {
        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, true);
        mTrampoline.setBackupServiceActive(NON_USER_SYSTEM, false);

        assertFalse(RandomAccessFileUtils.readBoolean(
                TrampolineTestable.sRememberActivatedFiles.get(NON_USER_SYSTEM), true));
    }

    @Test
    public void restoreAtInstallForUser_forwarded() throws Exception {

        mTrampoline.restoreAtInstallForUser(mUserId, PACKAGE_NAME, 123);

        verify(mBackupManagerServiceMock).restoreAtInstall(mUserId, PACKAGE_NAME, 123);
    }

    @Test
    public void restoreAtInstall_forwarded() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.restoreAtInstall(PACKAGE_NAME, 123);

        verify(mBackupManagerServiceMock).restoreAtInstall(mUserId, PACKAGE_NAME, 123);
    }

    @Test
    public void setBackupEnabledForUser_forwarded() throws Exception {

        mTrampoline.setBackupEnabledForUser(mUserId, true);

        verify(mBackupManagerServiceMock).setBackupEnabled(mUserId, true);
    }

    @Test
    public void setBackupEnabled_forwardedToCallingUserId() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.setBackupEnabled(true);

        verify(mBackupManagerServiceMock).setBackupEnabled(mUserId, true);
    }

    @Test
    public void setAutoRestoreForUser_forwarded() throws Exception {

        mTrampoline.setAutoRestoreForUser(mUserId, true);

        verify(mBackupManagerServiceMock).setAutoRestore(mUserId, true);
    }

    @Test
    public void setAutoRestore_forwarded() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.setAutoRestore(true);

        verify(mBackupManagerServiceMock).setAutoRestore(mUserId, true);
    }

    @Test
    public void isBackupEnabledForUser_forwarded() throws Exception {

        mTrampoline.isBackupEnabledForUser(mUserId);

        verify(mBackupManagerServiceMock).isBackupEnabled(mUserId);
    }

    @Test
    public void isBackupEnabled_forwardedToCallingUserId() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.isBackupEnabled();

        verify(mBackupManagerServiceMock).isBackupEnabled(mUserId);
    }

    @Test
    public void setBackupPassword_forwarded() throws Exception {
        mTrampoline.setBackupPassword(CURRENT_PASSWORD, NEW_PASSWORD);
        verify(mBackupManagerServiceMock).setBackupPassword(CURRENT_PASSWORD, NEW_PASSWORD);
    }

    @Test
    public void hasBackupPassword_forwarded() throws Exception {
        mTrampoline.hasBackupPassword();
        verify(mBackupManagerServiceMock).hasBackupPassword();
    }

    @Test
    public void backupNowForUser_forwarded() throws Exception {

        mTrampoline.backupNowForUser(mUserId);

        verify(mBackupManagerServiceMock).backupNow(mUserId);
    }

    @Test
    public void backupNow_forwardedToCallingUserId() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.backupNow();

        verify(mBackupManagerServiceMock).backupNow(mUserId);
    }

    @Test
    public void adbBackup_forwarded() throws Exception {
        mTrampoline.adbBackup(mUserId, mParcelFileDescriptorMock, true, true,
                true, true, true, true, true, true,
                PACKAGE_NAMES);
        verify(mBackupManagerServiceMock).adbBackup(mUserId, mParcelFileDescriptorMock, true,
                true, true, true, true, true, true, true, PACKAGE_NAMES);
    }

    @Test
    public void fullTransportBackupForUser_forwarded() throws Exception {

        mTrampoline.fullTransportBackupForUser(mUserId, PACKAGE_NAMES);

        verify(mBackupManagerServiceMock).fullTransportBackup(mUserId, PACKAGE_NAMES);
    }

    @Test
    public void adbRestore_forwarded() throws Exception {
        mTrampoline.adbRestore(mUserId, mParcelFileDescriptorMock);
        verify(mBackupManagerServiceMock).adbRestore(mUserId, mParcelFileDescriptorMock);
    }

    @Test
    public void acknowledgeFullBackupOrRestoreForUser_forwarded() throws Exception {

        mTrampoline.acknowledgeFullBackupOrRestoreForUser(
                mUserId,
                123,
                true,
                CURRENT_PASSWORD,
                ENCRYPTION_PASSWORD,
                mFullBackupRestoreObserverMock);

        verify(mBackupManagerServiceMock)
                .acknowledgeAdbBackupOrRestore(
                        mUserId,
                        123,
                        true,
                        CURRENT_PASSWORD,
                        ENCRYPTION_PASSWORD,
                        mFullBackupRestoreObserverMock);
    }

    @Test
    public void acknowledgeFullBackupOrRestore_forwarded() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.acknowledgeFullBackupOrRestore(123, true, CURRENT_PASSWORD, ENCRYPTION_PASSWORD,
                mFullBackupRestoreObserverMock);

        verify(mBackupManagerServiceMock)
                .acknowledgeAdbBackupOrRestore(
                        mUserId,
                        123,
                        true,
                        CURRENT_PASSWORD,
                        ENCRYPTION_PASSWORD,
                        mFullBackupRestoreObserverMock);
    }

    @Test
    public void selectBackupTransportAsyncForUser_beforeUserUnlocked_notifiesBackupNotAllowed()
            throws Exception {
        mUserServices.clear();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        ISelectBackupTransportCallback listener =
                new ISelectBackupTransportCallback.Stub() {
                    @Override
                    public void onSuccess(String transportName) {
                        future.completeExceptionally(new AssertionError());
                    }
                    @Override
                    public void onFailure(int reason) {
                        future.complete(reason);
                    }
                };

        mTrampoline.selectBackupTransportAsyncForUser(mUserId, TRANSPORT_COMPONENT_NAME, listener);

        assertEquals(BackupManager.ERROR_BACKUP_NOT_ALLOWED, (int) future.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void selectBackupTransportAsyncForUser_beforeUserUnlockedWithNullListener_doesNotThrow()
            throws Exception {
        mTrampoline.selectBackupTransportAsyncForUser(mUserId, TRANSPORT_COMPONENT_NAME, null);

        // No crash.
    }

    @Test
    public void
            selectBackupTransportAsyncForUser_beforeUserUnlockedWithThrowingListener_doesNotThrow()
            throws Exception {
        ISelectBackupTransportCallback.Stub listener =
                new ISelectBackupTransportCallback.Stub() {
                    @Override
                    public void onSuccess(String transportName) {}
                    @Override
                    public void onFailure(int reason) throws RemoteException {
                        throw new RemoteException();
                    }
                };

        mTrampoline.selectBackupTransportAsyncForUser(mUserId, TRANSPORT_COMPONENT_NAME, listener);

        // No crash.
    }

    @Test
    public void getAvailableRestoreTokenForUser_forwarded() {
        when(mBackupManagerServiceMock.getAvailableRestoreToken(mUserId, PACKAGE_NAME))
                .thenReturn(123L);

        assertEquals(123, mTrampoline.getAvailableRestoreTokenForUser(mUserId, PACKAGE_NAME));
        verify(mBackupManagerServiceMock).getAvailableRestoreToken(mUserId, PACKAGE_NAME);
    }

    @Test
    public void isAppEligibleForBackupForUser_forwarded() {
        when(mBackupManagerServiceMock.isAppEligibleForBackup(mUserId, PACKAGE_NAME))
                .thenReturn(true);

        assertTrue(mTrampoline.isAppEligibleForBackupForUser(mUserId, PACKAGE_NAME));
        verify(mBackupManagerServiceMock).isAppEligibleForBackup(mUserId, PACKAGE_NAME);
    }

    @Test
    public void requestBackupForUser_forwarded() throws Exception {
        when(mBackupManagerServiceMock.requestBackup(mUserId, PACKAGE_NAMES,
                mBackupObserverMock, mBackupManagerMonitorMock, 123)).thenReturn(456);

        assertEquals(456, mTrampoline.requestBackupForUser(mUserId, PACKAGE_NAMES,
                mBackupObserverMock, mBackupManagerMonitorMock, 123));
        verify(mBackupManagerServiceMock).requestBackup(mUserId, PACKAGE_NAMES,
                mBackupObserverMock, mBackupManagerMonitorMock, 123);
    }

    @Test
    public void requestBackup_forwardedToCallingUserId() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;
        when(mBackupManagerServiceMock.requestBackup(mUserId, PACKAGE_NAMES,
                mBackupObserverMock, mBackupManagerMonitorMock, 123)).thenReturn(456);

        assertEquals(456, mTrampoline.requestBackup(PACKAGE_NAMES,
                mBackupObserverMock, mBackupManagerMonitorMock, 123));
        verify(mBackupManagerServiceMock).requestBackup(mUserId, PACKAGE_NAMES,
                mBackupObserverMock, mBackupManagerMonitorMock, 123);
    }

    @Test
    public void cancelBackupsForUser_forwarded() throws Exception {

        mTrampoline.cancelBackupsForUser(mUserId);

        verify(mBackupManagerServiceMock).cancelBackups(mUserId);
    }

    @Test
    public void cancelBackups_forwardedToCallingUserId() throws Exception {
        TrampolineTestable.sCallingUserId = mUserId;

        mTrampoline.cancelBackups();

        verify(mBackupManagerServiceMock).cancelBackups(mUserId);
    }

    @Test
    public void beginFullBackup_forwarded() throws Exception {
        FullBackupJob fullBackupJob = new FullBackupJob();
        when(mBackupManagerServiceMock.beginFullBackup(mUserId, fullBackupJob)).thenReturn(true);

        assertTrue(mTrampoline.beginFullBackup(mUserId, fullBackupJob));
        verify(mBackupManagerServiceMock).beginFullBackup(mUserId, fullBackupJob);
    }

    @Test
    public void endFullBackup_forwarded() {
        mTrampoline.endFullBackup(mUserId);
        verify(mBackupManagerServiceMock).endFullBackup(mUserId);
    }

    @Test
    public void dump_callerDoesNotHavePermission_ignored() {
        when(mContextMock.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        mTrampoline.dump(mFileDescriptorStub, mPrintWriterMock, new String[0]);

        verifyNoMoreInteractions(mBackupManagerServiceMock);
    }

    @Test
    public void dump_callerHasPermission_forwarded() {
        when(mContextMock.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP)).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        mTrampoline.dump(mFileDescriptorStub, mPrintWriterMock, null);

        verify(mBackupManagerServiceMock).dump(mFileDescriptorStub, mPrintWriterMock, null);
    }

    public void testGetUserForAncestralSerialNumber() {
        TrampolineTestable.sBackupDisabled = false;
        Trampoline trampoline = new TrampolineTestable(mContextMock, mUserServices);

        trampoline.getUserForAncestralSerialNumber(0L);
        verify(mBackupManagerServiceMock).getUserForAncestralSerialNumber(anyInt());
    }

    public void testGetUserForAncestralSerialNumber_whenDisabled() {
        TrampolineTestable.sBackupDisabled = true;
        Trampoline trampoline = new TrampolineTestable(mContextMock, mUserServices);

        trampoline.getUserForAncestralSerialNumber(0L);
        verify(mBackupManagerServiceMock, never()).getUserForAncestralSerialNumber(anyInt());
    }

    private static class TrampolineTestable extends Trampoline {
        static boolean sBackupDisabled = false;
        static int sCallingUserId = -1;
        static int sCallingUid = -1;
        static BackupManagerService sBackupManagerServiceMock = null;
        static File sSuppressFile = null;
        static SparseArray<File> sActivatedFiles = new SparseArray<>();
        static SparseArray<File> sRememberActivatedFiles = new SparseArray<>();
        static UserManager sUserManagerMock = null;

        TrampolineTestable(Context context, SparseArray<UserBackupManagerService> userServices) {
            super(context, userServices);
            mService = sBackupManagerServiceMock;
        }

        @Override
        protected UserManager getUserManager() {
            return sUserManagerMock;
        }

        @Override
        protected boolean isBackupDisabled() {
            return sBackupDisabled;
        }

        @Override
        protected File getSuppressFileForSystemUser() {
            return sSuppressFile;
        }

        @Override
        protected File getRememberActivatedFileForNonSystemUser(int userId) {
            return sRememberActivatedFiles.get(userId);
        }

        @Override
        protected File getActivatedFileForNonSystemUser(int userId) {
            return sActivatedFiles.get(userId);
        }

        protected int binderGetCallingUserId() {
            return sCallingUserId;
        }

        @Override
        protected int binderGetCallingUid() {
            return sCallingUid;
        }

        @Override
        protected void postToHandler(Runnable runnable) {
            runnable.run();
        }
    }
}
