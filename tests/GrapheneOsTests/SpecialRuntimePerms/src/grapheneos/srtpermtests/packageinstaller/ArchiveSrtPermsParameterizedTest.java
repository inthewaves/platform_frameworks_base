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

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.Flags;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ArchiveCompatibilityParams;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.UnarchivalState;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.SearchCondition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.install.lib.LocalIntentSender;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Based on cts/tests/tests/packageinstaller/uninstall/src/android/packageinstaller/uninstall/cts/ArchiveTest.java
 * <p>
 * Tests that the granted states of special runtime permissions are preserved after unarchiving.
 */
// @RunWith(AndroidJUnit4.class)
@RunWith(Parameterized.class)
@AppModeFull
public class ArchiveSrtPermsParameterizedTest {
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

    private static final String LOG_TAG = ArchiveSrtPermsParameterizedTest.class.getSimpleName();

    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/uninstall/";
    private static final String ARCHIVE_APK = SAMPLE_APK_BASE
            + "GtsArchiveTestApp.apk";
    private static final String ARCHIVE_APP_PACKAGE_NAME =
            "android.packageinstaller.archive.cts.archiveapp";
    private static final String ARCHIVE_APP_ACTIVITY_NAME =
            ARCHIVE_APP_PACKAGE_NAME + ".MainActivity";
    private static final String SYSTEM_PACKAGE_NAME = "android";

    private static final String HELLO_WORLD_PACKAGE_NAME = "com.example.helloworld";
    private static final String HELLO_WORLD_V1_APK = SAMPLE_APK_BASE
            + "GosHelloWorldAppV1.apk";
    private static final String HELLO_WORLD_V2_APK = SAMPLE_APK_BASE
            + "GosHelloWorldAppV2.apk";

    private static final long TIMEOUT_MS = 30000;

    private static CompletableFuture<Integer> sUnarchiveId;
    private static CompletableFuture<String> sUnarchiveReceiverPackageName;
    private static CompletableFuture<Boolean> sUnarchiveReceiverAllUsers;
    private static CompletableFuture<Integer> sInstallResult;
    private static CompletableFuture<String> sInstallResultMessage;

    private Context mContext;
    private UiDevice mUiDevice;
    private PackageManager mPackageManager;
    private PackageInstaller mPackageInstaller;
    private LauncherApps mLauncherApps;
    private String mDefaultHome;
    private PermissionManager mPermissionManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws Exception {
        assumeTrue("Form factor is not supported", isFormFactorSupported());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        mPermissionManager = mContext.getSystemService(PermissionManager.class);
        mPackageManager = mContext.getPackageManager();
        mPackageInstaller = mPackageManager.getPackageInstaller();
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, UnarchiveBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // Unblock UI
        mUiDevice = UiDevice.getInstance(instrumentation);
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        AppOpsUtils.reset(mContext.getPackageName());
        sUnarchiveId = new CompletableFuture<>();
        sUnarchiveReceiverPackageName = new CompletableFuture<>();
        sUnarchiveReceiverAllUsers = new CompletableFuture<>();
        sInstallResult = new CompletableFuture<>();
        sInstallResultMessage = new CompletableFuture<>();
        mDefaultHome = getDefaultLauncher(instrumentation);
        ArchiveCompatibilityParams options = new ArchiveCompatibilityParams();
        options.setEnableUnarchivalConfirmation(false);
        mLauncherApps.setArchiveCompatibility(options);
        // Prepare device to same state to make tests more independent.
        prepareDevice();
        abandonPendingUnarchivalSessions();
    }

    @After
    public void tearDown() {
        uninstallPackage(ARCHIVE_APP_PACKAGE_NAME);
        if (mDefaultHome != null) {
            setDefaultLauncher(InstrumentationRegistry.getInstrumentation(), mDefaultHome);
        }
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private void dumpWindowHierarchy() throws InterruptedException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mUiDevice.dumpWindowHierarchy(outputStream);
        String windowHierarchy = outputStream.toString(StandardCharsets.UTF_8.name());

        Log.w(LOG_TAG, "Window hierarchy:");
        for (String line : windowHierarchy.split("\n")) {
            Thread.sleep(10);
            Log.w(LOG_TAG, line);
        }
    }

    private UiObject2 waitFor(SearchCondition<UiObject2> condition)
            throws IOException, InterruptedException {
        final long OneSecond = TimeUnit.SECONDS.toMillis(1);
        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < TIMEOUT_MS) {
            try {
                var result = mUiDevice.wait(condition, OneSecond);
                if (result == null) {
                    continue;
                }
                return result;
            } catch (Throwable e) {
                Thread.sleep(OneSecond);
            }
        }
        dumpWindowHierarchy();
        return null;
    }

    private int getExpectedPermissionResult(boolean isExpected) {
        return isExpected ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
    }

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

            makeUnarchiveRequest(ARCHIVE_APP_PACKAGE_NAME);
            final int unarchiveId = sUnarchiveId.get(10, TimeUnit.SECONDS);
            assertThat(unarchiveId).isGreaterThan(0);
            completeUnarchiveRequest(ARCHIVE_APP_PACKAGE_NAME, ARCHIVE_APK, unarchiveId);

            assertThat(sInstallResult.get(10, TimeUnit.SECONDS)).isEqualTo(
                    PackageInstaller.STATUS_SUCCESS);
            assertTrue(isInstalled());

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

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveAppWithUpgradeVersion_specialRuntimePermissonsPreserved() throws Exception {
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

        makeUnarchiveRequest(HELLO_WORLD_PACKAGE_NAME);

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

    private void makeUnarchiveRequest(final String packageName) throws Exception {
        LocalIntentSender archiveSender = new LocalIntentSender();
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(packageName,
                            archiveSender.getIntentSender());
                    Intent archiveIntent = archiveSender.getResult();
                    assertThat(archiveIntent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            -100)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        SessionListener sessionListener = new SessionListener();
        mPackageInstaller.registerSessionCallback(sessionListener,
                new Handler(Looper.getMainLooper()));

        LocalIntentSender unarchiveSender = new LocalIntentSender();
        mPackageInstaller.requestUnarchive(packageName,
                unarchiveSender.getIntentSender());
        Intent unarchiveIntent = unarchiveSender.pollResult(5, TimeUnit.SECONDS);
        assertThat(unarchiveIntent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)).isEqualTo(
                packageName);
        assertThat(unarchiveIntent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_STATUS,
                -100)).isEqualTo(
                PackageInstaller.STATUS_PENDING_USER_ACTION);

        Intent unarchiveExtraIntent = unarchiveIntent.getParcelableExtra(Intent.EXTRA_INTENT,
                Intent.class);
        unarchiveExtraIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        prepareDevice();
        mContext.startActivity(unarchiveExtraIntent);
        mUiDevice.waitForIdle();

        assertThat(waitFor(Until.findObject(By.textContains("Restore")))).isNotNull();

        UiObject2 clickableView = mUiDevice.findObject(By.res(SYSTEM_PACKAGE_NAME, "button1"));
        if (clickableView == null) {
            Assert.fail("Restore button not shown");
        }
        clickableView.click();
    }

    private void completeUnarchiveRequest(final String packageName, final String apkPath,
            int unarchiveId) throws NameNotFoundException, IOException {
        // Complete the unarchive request by installing the app back. Assert that the installation
        // goes through without any additional confirmation dialog.
        mPackageInstaller.reportUnarchivalState(
                UnarchivalState.createOkState(unarchiveId));
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        final int sessionId = mPackageInstaller.createSession(params);
        assertThat(sessionId).isEqualTo(unarchiveId);
        PackageInstaller.Session session = mPackageInstaller.openSession(sessionId);
        File apkFile = new File(apkPath);
        try (OutputStream os = session.openWrite("base.apk", 0, apkFile.length());
             InputStream is = new FileInputStream(apkFile)) {
            writeFullStream(is, os, apkFile.length());
        }
        var installResultReceiver = new InstallResultReceiver();
        session.commit(installResultReceiver.getIntentSender(mContext));
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
                                        long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertThat(total).isEqualTo(expected);
        }
    }

    private void prepareDevice() throws Exception {
        mUiDevice.waitForIdle();
        // wake up the screen
        mUiDevice.wakeUp();
        // unlock the keyguard or the expected window is by systemui or other alert window
        mUiDevice.pressMenu();
        // dismiss the system alert window for requesting permissions
        mUiDevice.pressBack();
        // return to home/launcher to prevent from being obscured by systemui or other alert window
        mUiDevice.pressHome();
        // Wait for device idle
        mUiDevice.waitForIdle();
    }

    private void installPackage(@NonNull String path) {
        installPackage(path, mContext.getPackageName());
    }

    private void installPackage(@NonNull String path, @NonNull String installerPackageName) {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                String.format("pm install -r -i %s -t -g %s", installerPackageName, path)));
    }

    private boolean isInstalled() {
        return isInstalled(ARCHIVE_APP_PACKAGE_NAME);
    }

    private boolean isInstalled(String packageName) {
        Log.d(LOG_TAG, "Testing if package " + packageName + " is installed for user "
                + mContext.getUser());
        try {
            mContext.getPackageManager().getPackageInfo(packageName, /* flags= */ 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(LOG_TAG, "Package " + packageName + " not installed for user "
                    + mContext.getUser() + ": " + e);
            return false;
        }
    }

    private void abandonPendingUnarchivalSessions() {
        List<PackageInstaller.SessionInfo> sessions = mPackageInstaller.getAllSessions();
        for (PackageInstaller.SessionInfo session : sessions) {
            if (TextUtils.equals(ARCHIVE_APP_PACKAGE_NAME, session.getAppPackageName())
                    && TextUtils.equals(mContext.getPackageName(),
                        session.getInstallerPackageName())) {
                // The test app cannot abandon draft sessions
                try {
                    mPackageInstaller.abandonSession(session.getSessionId());
                } catch (SecurityException ignored) {
                }
            }
        }
    }

    private static boolean isFormFactorSupported() {
        return !FeatureUtil.isArc()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isVrHeadset();
    }

    public static class UnarchiveBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.equals(Intent.ACTION_UNARCHIVE_PACKAGE, intent.getAction())) {
                return;
            }
            if (sUnarchiveId == null) {
                sUnarchiveId = new CompletableFuture<>();
            }
            sUnarchiveId.complete(intent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_ID, -1));
            if (sUnarchiveReceiverPackageName == null) {
                sUnarchiveReceiverPackageName = new CompletableFuture<>();
            }
            sUnarchiveReceiverPackageName.complete(
                    intent.getStringExtra(PackageInstaller.EXTRA_UNARCHIVE_PACKAGE_NAME));
            if (sUnarchiveReceiverAllUsers == null) {
                sUnarchiveReceiverAllUsers = new CompletableFuture<>();
            }
            sUnarchiveReceiverAllUsers.complete(
                    intent.getBooleanExtra(PackageInstaller.EXTRA_UNARCHIVE_ALL_USERS,
                            true /* defaultValue */));
        }
    }

    private static class InstallResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            sInstallResult.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE));
            sInstallResultMessage.complete(intent.getStringExtra(
                    PackageInstaller.EXTRA_STATUS_MESSAGE));

        }

        public IntentSender getIntentSender(Context context) {
            // Generate a unique string to ensure each LocalIntentSender gets its own results.
            String action = InstallResultReceiver.class.getName();
            context.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED);
            Intent intent = new Intent(action).setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                    FLAG_UPDATE_CURRENT | FLAG_MUTABLE);
            return pending.getIntentSender();
        }
    }

    static class SessionListener extends PackageInstaller.SessionCallback {

        final CompletableFuture<Integer> mSessionIdCreated = new CompletableFuture<>();
        final CompletableFuture<Integer> mSessionIdFinished = new CompletableFuture<>();

        @Override
        public void onCreated(int sessionId) {
            mSessionIdCreated.complete(sessionId);
        }

        @Override
        public void onBadgingChanged(int sessionId) {
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) {
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            mSessionIdFinished.complete(sessionId);
        }
    }

    public static class Launcher extends Activity {
    }
}
