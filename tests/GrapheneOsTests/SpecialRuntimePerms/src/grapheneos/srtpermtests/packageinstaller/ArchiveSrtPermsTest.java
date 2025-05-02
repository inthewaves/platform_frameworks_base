/*
 * Copyright (C) 2023 The Android Open Source Project
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
package grapheneos.srtpermtests.packageinstaller;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
//import android.content.IIntentReceiver;
//import android.content.IIntentSender;
import android.content.pm.Flags;
import android.content.pm.PackageInstaller;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import grapheneos.test.common.SensorsSettingsUtil;

/**
 * Based on cts/tests/tests/packageinstaller/uninstall/src/android/packageinstaller/uninstall/cts/ArchiveTest.java
 * <p>
 * Tests that the granted states of special runtime permissions are preserved after unarchiving.
 */
// @RunWith(AndroidJUnit4.class)
@RunWith(Parameterized.class)
@AppModeFull
public class ArchiveSrtPermsTest extends BaseInstallerTest {
    private static final int NUM_PARAMS = 3;

    @Parameterized.Parameters(name = "{index}: INTERNET granted={0}, OTHER_SENSORS granted={1}, NOTIFICATIONS granted={2}")
    public static Collection<Object[]> data() {
        List<Object[]> list = new ArrayList<>();
        final int numPossibilities = 1 << NUM_PARAMS;
        for (int currentCode = 0; currentCode < numPossibilities; currentCode++) {
            final boolean internet = (currentCode & (1)) != 0;
            final boolean otherSensors = (currentCode & (1 << 1)) != 0;
            final boolean notifications = (currentCode & (1 << 2)) != 0;
            list.add(new Object[] {internet, otherSensors, notifications});
        }
        return list;
    }

    @Parameterized.Parameter
    public boolean mIsInternetGranted;

    @Parameterized.Parameter(1)
    public boolean mIsSensorGranted;

    /**
     * Use POST_NOTIFICATIONS to test that a normal, non-special permission also works
     */
    @Parameterized.Parameter(2)
    public boolean mIsNotificationGranted;

    @Override
    protected Set<String> getTestAppPackageNames() {
        return Set.of(TestApks.archiveApk.getPackageName(), TestApks.helloWorldV1.getPackageName());
    }

    private void grantAndRevokePermissions(final String pkgName, final boolean skipSensors) {
        runWithShellPermissionIdentity(
                () -> {
                    final UserHandle user = UserHandle.of(mContext.getUserId());
                    if (mIsInternetGranted) {
                        mPackageManager.grantRuntimePermission(
                                pkgName,
                                Manifest.permission.INTERNET,
                                user);
                    } else {
                        mPackageManager.revokeRuntimePermission(
                                pkgName,
                                Manifest.permission.INTERNET,
                                user);
                    }
                    if (!skipSensors) {
                        if (mIsSensorGranted) {
                            mPackageManager.grantRuntimePermission(
                                    pkgName,
                                    Manifest.permission.OTHER_SENSORS,
                                    user);
                        } else {
                            mPackageManager.revokeRuntimePermission(
                                    pkgName,
                                    Manifest.permission.OTHER_SENSORS,
                                    user);
                        }
                    }
                    if (mIsNotificationGranted) {
                        mPackageManager.grantRuntimePermission(
                                pkgName,
                                Manifest.permission.POST_NOTIFICATIONS,
                                user);
                    } else {
                        mPackageManager.revokeRuntimePermission(
                                pkgName,
                                Manifest.permission.POST_NOTIFICATIONS,
                                user);
                    }
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveApp_specialRuntimePermissionsPreserved() throws Exception {
        final String pkgName = TestApks.archiveApk.getPackageName();
        final String apkFile = TestApks.archiveApk.getApkPath();

        installPackage(apkFile);
        try {
            grantAndRevokePermissions(pkgName, false);

            assertEquals(
               "expected INTERNET permission granted state to be " + mIsInternetGranted,
                    getExpectedPermissionResult(mIsInternetGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.INTERNET, pkgName));
            assertEquals(
                    "expected OTHER_SENSORS permission granted state to be " + mIsSensorGranted,
                    getExpectedPermissionResult(mIsSensorGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.OTHER_SENSORS, pkgName));
            assertEquals(
                    "expected POST_NOTIFICATIONS permission granted state to be " + mIsNotificationGranted,
                    getExpectedPermissionResult(mIsNotificationGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.POST_NOTIFICATIONS, pkgName));

            archiveThenMakeUnarchiveRequest(pkgName);
            final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
            assertThat(unarchiveId).isGreaterThan(0);
            completeUnarchiveRequest(pkgName, apkFile, unarchiveId);

            assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                    PackageInstaller.STATUS_SUCCESS);
            assertTrue(isInstalled(pkgName));

            assertEquals(
                    "expected INTERNET permission=" + mIsInternetGranted +  " after unarchive",
                    getExpectedPermissionResult(mIsInternetGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.INTERNET, pkgName));
            assertEquals(
                    "expected OTHER_SENSORS permission=" + mIsSensorGranted +  " after unarchive",
                    getExpectedPermissionResult(mIsSensorGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.OTHER_SENSORS, pkgName));
            assertEquals(
                    "expected POST_NOTIFICATIONS permission=" + mIsNotificationGranted +  " after unarchive",
                    getExpectedPermissionResult(mIsNotificationGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.POST_NOTIFICATIONS, pkgName));
        } finally {
            uninstallPackage(pkgName);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveApp_specialRuntimePermissionsPreservedWithSensorSettings() {
        SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, mIsSensorGranted, () -> {
            assertEquals(mIsSensorGranted, SensorsSettingsUtil.getAutoGrantSensorsSetting(mInstrumentation));

            // FIXME: Fix the auto grant sensors setting not working with pm install...
            // installPackage(ARCHIVE_APK);

            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
            try {
                installApkByInstallerSession(TestApks.archiveApk);
            } finally {
                mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
            }

            try {
                grantAndRevokePermissions(TestApks.archiveApk.getPackageName(), true);

                assertEquals(
                        "expected INTERNET permission granted state to be " + mIsInternetGranted,
                        getExpectedPermissionResult(mIsInternetGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.INTERNET, TestApks.archiveApk.getPackageName()));
                assertEquals(
                        "expected OTHER_SENSORS permission granted state to be "
                                + mIsSensorGranted
                                + " from auto grant setting",
                        getExpectedPermissionResult(mIsSensorGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.OTHER_SENSORS, TestApks.archiveApk.getPackageName()));
                assertEquals(
                        "expected POST_NOTIFICATIONS permission granted state to be " + mIsNotificationGranted,
                        getExpectedPermissionResult(mIsNotificationGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.POST_NOTIFICATIONS, TestApks.archiveApk.getPackageName()));

                archiveThenMakeUnarchiveRequest(TestApks.archiveApk.getPackageName());
                final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
                assertThat(unarchiveId).isGreaterThan(0);
                completeUnarchiveRequest(
                        TestApks.archiveApk.getPackageName(),
                        TestApks.archiveApk.getApkPath(),
                        unarchiveId);

                assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                        PackageInstaller.STATUS_SUCCESS);
                assertTrue(isInstalled(TestApks.archiveApk.getPackageName()));

                assertEquals(
                        "expected INTERNET permission=" + mIsInternetGranted +  " after unarchive",
                        getExpectedPermissionResult(mIsInternetGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.INTERNET, TestApks.archiveApk.getPackageName()));
                assertEquals(
                        "expected OTHER_SENSORS permission=" + mIsSensorGranted +  " after unarchive",
                        getExpectedPermissionResult(mIsSensorGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.OTHER_SENSORS, TestApks.archiveApk.getPackageName()));
                assertEquals(
                        "expected POST_NOTIFICATIONS permission=" + mIsNotificationGranted +  " after unarchive",
                        getExpectedPermissionResult(mIsNotificationGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.POST_NOTIFICATIONS, TestApks.archiveApk.getPackageName()));
            } finally {
                uninstallPackage(TestApks.archiveApk.getPackageName());
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveAppWithUpgradeVersion_specialRuntimePermissionsPreserved() throws Exception {
        installPackage(TestApks.helloWorldV1.getApkPath());

        grantAndRevokePermissions(TestApks.helloWorldV1.getPackageName(), false);

        assertEquals(
                "expected INTERNET permission=" + mIsInternetGranted,
                getExpectedPermissionResult(mIsInternetGranted),
                mPackageManager.checkPermission(
                        Manifest.permission.INTERNET, TestApks.helloWorldV1.getPackageName()));
        assertEquals(
                "expected OTHER_SENSORS permission=" + mIsSensorGranted,
                getExpectedPermissionResult(mIsSensorGranted),
                mPackageManager.checkPermission(
                        Manifest.permission.OTHER_SENSORS, TestApks.helloWorldV1.getPackageName()));
        assertEquals(
                "expected POST_NOTIFICATIONS permission=" + mIsNotificationGranted,
                getExpectedPermissionResult(mIsNotificationGranted),
                mPackageManager.checkPermission(
                        Manifest.permission.POST_NOTIFICATIONS, TestApks.helloWorldV1.getPackageName()));

        archiveThenMakeUnarchiveRequest(TestApks.helloWorldV1.getPackageName());

        // Complete the unarchive request by installing the updated version app. Assert that
        // the installation goes through without any additional confirmation dialog.
        final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
        assertThat(unarchiveId).isGreaterThan(0);
        try {
            completeUnarchiveRequest(
                    TestApks.helloWorldV1.getPackageName(),
                    TestApks.helloWorldV2.getApkPath(), unarchiveId);
            assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                    PackageInstaller.STATUS_SUCCESS);
            assertTrue(isInstalled(TestApks.helloWorldV2.getPackageName()));

            assertEquals(
                    "expected INTERNET permission granted state to be "
                            + mIsInternetGranted + " after unarchive",
                    getExpectedPermissionResult(mIsInternetGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.INTERNET, TestApks.helloWorldV2.getPackageName()));
            assertEquals(
                    "expected OTHER_SENSORS permission granted state to be "
                            + mIsSensorGranted + " after unarchive",
                    getExpectedPermissionResult(mIsSensorGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.OTHER_SENSORS, TestApks.helloWorldV2.getPackageName()));
            assertEquals(
                    "expected POST_NOTIFICATIONS permission granted state to be "
                            + mIsNotificationGranted + " after unarchive",
                    getExpectedPermissionResult(mIsNotificationGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.POST_NOTIFICATIONS, TestApks.helloWorldV2.getPackageName()));
        } finally {
            // Uninstall the hello world package to avoid unexpected errors
            uninstallPackage(TestApks.helloWorldV2.getPackageName());

            // The test app cannot abandon draft sessions
            try {
                mPackageInstaller.abandonSession(unarchiveId);
            } catch (SecurityException ignored) {
            }
        }
    }
}
