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
import android.content.Intent;
import android.content.pm.Flags;
import android.content.pm.PackageInstaller;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.cts.install.lib.LocalIntentSender;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
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
public class ArchiveSrtPermsTest extends BaseArchiveTest {
    @Parameterized.Parameters(name = "{index}: INTERNET granted={0}, OTHER_SENSORS granted={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { false, false }, { false, true }, { true, false }, { true, true }
        });
    }

    @Parameterized.Parameter
    public boolean mIsInternetGranted;

    @Parameterized.Parameter(1)
    public boolean mIsSensorGranted;


    private static final String ARCHIVE_APK = SAMPLE_APK_BASE
            + "GtsArchiveTestApp.apk";
    private static final String ARCHIVE_APP_PACKAGE_NAME =
            "android.packageinstaller.archive.cts.archiveapp";

    @Override
    protected String[] getTestAppPackagesList() {
        return new String[] { ARCHIVE_APP_PACKAGE_NAME };
    }

    private static final String HELLO_WORLD_PACKAGE_NAME = "com.example.helloworld";
    private static final String HELLO_WORLD_V1_APK = SAMPLE_APK_BASE
            + "GosHelloWorldAppV1.apk";
    private static final String HELLO_WORLD_V2_APK = SAMPLE_APK_BASE
            + "GosHelloWorldAppV2.apk";

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveApp_specialRuntimePermissionsPreserved() throws Exception {
        installPackage(ARCHIVE_APK);
        try {
            runWithShellPermissionIdentity(
                    () -> {
                        final UserHandle user = UserHandle.of(mContext.getUserId());
                        if (mIsInternetGranted) {
                            mPackageManager.grantRuntimePermission(
                                    ARCHIVE_APP_PACKAGE_NAME,
                                    Manifest.permission.INTERNET,
                                    user);
                        } else {
                            mPackageManager.revokeRuntimePermission(
                                    ARCHIVE_APP_PACKAGE_NAME,
                                    Manifest.permission.INTERNET,
                                    user);
                        }

                        if (mIsSensorGranted) {
                            mPackageManager.grantRuntimePermission(
                                    ARCHIVE_APP_PACKAGE_NAME,
                                    Manifest.permission.OTHER_SENSORS,
                                    user);
                        } else {
                            mPackageManager.revokeRuntimePermission(
                                    ARCHIVE_APP_PACKAGE_NAME,
                                    Manifest.permission.OTHER_SENSORS,
                                    user);
                        }
                    },
                    Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                    Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);

            assertEquals(
               "expected INTERNET permission granted state to be " + mIsInternetGranted,
                    getExpectedPermissionResult(mIsInternetGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.INTERNET, ARCHIVE_APP_PACKAGE_NAME));
            assertEquals(
                    "expected OTHER_SENSORS permission granted state to be " + mIsSensorGranted,
                    getExpectedPermissionResult(mIsSensorGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.OTHER_SENSORS, ARCHIVE_APP_PACKAGE_NAME));

            archiveThenMakeUnarchiveRequest(ARCHIVE_APP_PACKAGE_NAME);
            final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
            assertThat(unarchiveId).isGreaterThan(0);
            completeUnarchiveRequest(ARCHIVE_APP_PACKAGE_NAME, ARCHIVE_APK, unarchiveId);

            assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                    PackageInstaller.STATUS_SUCCESS);
            assertTrue(isInstalled(ARCHIVE_APP_PACKAGE_NAME));

            assertEquals(
                    "expected INTERNET permission=" + mIsInternetGranted +  " after unarchive",
                    getExpectedPermissionResult(mIsInternetGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.INTERNET, ARCHIVE_APP_PACKAGE_NAME));
            assertEquals(
                    "expected OTHER_SENSORS permission=" + mIsSensorGranted +  " after unarchive",
                    getExpectedPermissionResult(mIsSensorGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.OTHER_SENSORS, ARCHIVE_APP_PACKAGE_NAME));
        } finally {
            uninstallPackage(ARCHIVE_APK);
        }
    }

    private void commitApk(final String packageName, final String apk, final Integer expectedStatus,
            final String expectedMsg) throws Exception {
        final PackageInstaller installer = mPackageInstaller;
        final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        // params.setAutoInstallDependenciesEnabled(enableAutoInstallDependencies);

        final int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        File file = new File(apk);
        try (OutputStream os = session.openWrite("test", 0, file.length());
             InputStream is = new FileInputStream(file)) {
            writeFullStream(is, os, file.length());
        }

        LocalIntentSender unarchiveSender = new LocalIntentSender();
        session.commit(unarchiveSender.getIntentSender());
        if (expectedStatus != null) {
            Intent unarchiveIntent = unarchiveSender.pollResult(10, TimeUnit.SECONDS);
            assertThat(unarchiveIntent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    Integer.MIN_VALUE)).isEqualTo(expectedStatus);
        }
        /*
        session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType,
                    IBinder allowlistToken, IIntentReceiver finishedReceiver,
                    String requiredPermission, Bundle options) {
                status.complete(
                        intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE));
                statusMessage.complete(
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }));
        */
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveApp_specialRuntimePermissionsPreservedWithSensorSettings() throws Exception {
        SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, mIsSensorGranted, () -> {
            assertEquals(mIsSensorGranted, SensorsSettingsUtil.getAutoGrantSensorsSetting(mInstrumentation));

            // FIXME: Fix the auto grant sensors setting not working with pm install...
            // installPackage(ARCHIVE_APK);

            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
            try {
                commitApk(
                        ARCHIVE_APP_PACKAGE_NAME, ARCHIVE_APK,
                        PackageInstaller.STATUS_SUCCESS, null);
            } finally {
                mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
            }

            try {
                runWithShellPermissionIdentity(
                        () -> {
                            final UserHandle user = UserHandle.of(mContext.getUserId());
                            if (mIsInternetGranted) {
                                mPackageManager.grantRuntimePermission(
                                        ARCHIVE_APP_PACKAGE_NAME,
                                        Manifest.permission.INTERNET,
                                        user);
                            } else {
                                mPackageManager.revokeRuntimePermission(
                                        ARCHIVE_APP_PACKAGE_NAME,
                                        Manifest.permission.INTERNET,
                                        user);
                            }
                        },
                        Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                        Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);

                assertEquals(
                        "expected INTERNET permission granted state to be " + mIsInternetGranted,
                        getExpectedPermissionResult(mIsInternetGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.INTERNET, ARCHIVE_APP_PACKAGE_NAME));
                assertEquals(
                        "expected OTHER_SENSORS permission granted state to be "
                                + mIsSensorGranted
                                + " from auto grant setting",
                        getExpectedPermissionResult(mIsSensorGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.OTHER_SENSORS, ARCHIVE_APP_PACKAGE_NAME));

                archiveThenMakeUnarchiveRequest(ARCHIVE_APP_PACKAGE_NAME);
                final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
                assertThat(unarchiveId).isGreaterThan(0);
                completeUnarchiveRequest(ARCHIVE_APP_PACKAGE_NAME, ARCHIVE_APK, unarchiveId);

                assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                        PackageInstaller.STATUS_SUCCESS);
                assertTrue(isInstalled(ARCHIVE_APP_PACKAGE_NAME));

                assertEquals(
                        "expected INTERNET permission=" + mIsInternetGranted +  " after unarchive",
                        getExpectedPermissionResult(mIsInternetGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.INTERNET, ARCHIVE_APP_PACKAGE_NAME));
                assertEquals(
                        "expected OTHER_SENSORS permission=" + mIsSensorGranted +  " after unarchive",
                        getExpectedPermissionResult(mIsSensorGranted),
                        mPackageManager.checkPermission(
                                Manifest.permission.OTHER_SENSORS, ARCHIVE_APP_PACKAGE_NAME));
            } finally {
                uninstallPackage(ARCHIVE_APK);
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveAppWithUpgradeVersion_specialRuntimePermissionsPreserved() throws Exception {
        installPackage(HELLO_WORLD_V1_APK);

        runWithShellPermissionIdentity(
                () -> {
                    final UserHandle user = UserHandle.of(mContext.getUserId());
                    if (mIsInternetGranted) {
                        mPackageManager.grantRuntimePermission(
                                HELLO_WORLD_PACKAGE_NAME,
                                Manifest.permission.INTERNET,
                                user);
                    } else {
                        mPackageManager.revokeRuntimePermission(
                                HELLO_WORLD_PACKAGE_NAME,
                                Manifest.permission.INTERNET,
                                user);
                    }

                    if (mIsSensorGranted) {
                        mPackageManager.grantRuntimePermission(
                                HELLO_WORLD_PACKAGE_NAME,
                                Manifest.permission.OTHER_SENSORS,
                                user);
                    } else {
                        mPackageManager.revokeRuntimePermission(
                                HELLO_WORLD_PACKAGE_NAME,
                                Manifest.permission.OTHER_SENSORS,
                                user);
                    }
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);

        assertEquals(
                "expected INTERNET permission=" + mIsInternetGranted +  " after unarchive",
                getExpectedPermissionResult(mIsInternetGranted),
                mPackageManager.checkPermission(
                        Manifest.permission.INTERNET, HELLO_WORLD_PACKAGE_NAME));
        assertEquals(
                "expected OTHER_SENSORS permission=" + mIsSensorGranted +  " after unarchive",
                getExpectedPermissionResult(mIsSensorGranted),
                mPackageManager.checkPermission(
                        Manifest.permission.OTHER_SENSORS, HELLO_WORLD_PACKAGE_NAME));

        archiveThenMakeUnarchiveRequest(HELLO_WORLD_PACKAGE_NAME);

        // Complete the unarchive request by installing the updated version app. Assert that
        // the installation goes through without any additional confirmation dialog.
        final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
        assertThat(unarchiveId).isGreaterThan(0);
        try {
            completeUnarchiveRequest(HELLO_WORLD_PACKAGE_NAME, HELLO_WORLD_V2_APK, unarchiveId);
            assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                    PackageInstaller.STATUS_SUCCESS);
            assertTrue(isInstalled(HELLO_WORLD_PACKAGE_NAME));

            assertEquals(
                    "expected INTERNET permission granted state to be " + mIsInternetGranted,
                    getExpectedPermissionResult(mIsInternetGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.INTERNET, HELLO_WORLD_PACKAGE_NAME));
            assertEquals(
                    "expected OTHER_SENSORS permission granted state to be " + mIsSensorGranted,
                    getExpectedPermissionResult(mIsSensorGranted),
                    mPackageManager.checkPermission(
                            Manifest.permission.OTHER_SENSORS, HELLO_WORLD_PACKAGE_NAME));
        } finally {
            // Uninstall the hello world package to avoid unexpected errors
            uninstallPackage(HELLO_WORLD_PACKAGE_NAME);

            // The test app cannot abandon draft sessions
            try {
                mPackageInstaller.abandonSession(unarchiveId);
            } catch (SecurityException ignored) {
            }
        }
    }
}
