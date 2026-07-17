package app.grapheneos.goscompat.securespawn;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.grapheneos.goscompat.securespawn.shared.SecureSpawnCmdlineCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnDumpableCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnFileDescriptorCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnHiddenApiCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnMediaProfilesCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnReflectiveDumpCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnSmapsCheck;
import app.grapheneos.goscompat.securespawn.shared.SecureSpawnTestApiCompatCheck;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class SecureSpawnDeviceTest {
    private static final String PACKAGE_NAME = "app.grapheneos.goscompat.securespawn";
    private static final String TAG = "GosCompatSecureSpawn";
    private static final long NATIVE_SERVICE_TIMEOUT_SECONDS = 10;
    private static final long WEBVIEW_TIMEOUT_SECONDS = 5;

    @Test
    public void execSpawned() {
        SecureSpawnCheck.ProcessState result = SecureSpawnCheck.processState();
        assertProcessState(result);
        assertWithMessage(failureMessage("expected execSpawned == true", result))
                .that(result.execSpawned()).isTrue();
        assertWithMessage(failureMessage("expected hardenedMallocDisabled == false", result))
                .that(result.hardenedMallocDisabled()).isFalse();
    }

    @Test
    public void notExecSpawned() {
        SecureSpawnCheck.ProcessState result = SecureSpawnCheck.processState();
        assertProcessState(result);
        assertWithMessage(failureMessage("expected execSpawned == false", result))
                .that(result.execSpawned()).isFalse();
        assertWithMessage(failureMessage("expected hardenedMallocDisabled == false", result))
                .that(result.hardenedMallocDisabled()).isFalse();
    }

    @Test
    public void notExecSpawnedCompatZygote() {
        SecureSpawnCheck.ProcessState result = SecureSpawnCheck.processState();
        assertProcessState(result);
        assertWithMessage(failureMessage("expected execSpawned == false", result))
                .that(result.execSpawned()).isFalse();
        assertWithMessage(failureMessage("expected hardenedMallocDisabled == true", result))
                .that(result.hardenedMallocDisabled()).isTrue();
    }

    @Test
    public void nativeServiceTerminatesOnSigterm() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CompletableFuture<IBinder> connected = new CompletableFuture<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                connected.complete(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        boolean bound = context.bindService(
                new Intent().setComponent(
                        ComponentName.createRelative(PACKAGE_NAME, ".NativeSignalProbeService")),
                connection,
                Context.BIND_AUTO_CREATE);
        try {
            assertWithMessage("expected native signal probe service bind to succeed")
                    .that(bound).isTrue();

            IBinder binder = connected.get(NATIVE_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            CountDownLatch terminated = new CountDownLatch(1);
            binder.linkToDeath(terminated::countDown, 0);
            INativeSignalProbe.Stub.asInterface(binder).terminateWithSigterm();

            assertWithMessage("expected SIGTERM to terminate native signal probe service")
                    .that(terminated.await(NATIVE_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        } finally {
            if (bound) {
                context.unbindService(connection);
            }
        }
    }

    @Test
    public void webViewRendererProcessGroupIsRemoved() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
        assertWithMessage("expected a current WebView provider")
                .that(webViewPackage).isNotNull();

        String providerPackageName = webViewPackage.packageName;
        Set<Integer> existingRendererPids = webViewRendererPids(providerPackageName);
        Intent intent = new Intent(instrumentation.getTargetContext(),
                WebViewProcessGroupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        WebViewProcessGroupActivity activity =
                (WebViewProcessGroupActivity) instrumentation.startActivitySync(intent);
        try {
            CountDownLatch pageLoaded = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> {
                WebView webView = activity.getWebView();
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        pageLoaded.countDown();
                    }
                });
                webView.loadData("<html><body>ready</body></html>", "text/html", "UTF-8");
            });
            assertWithMessage("expected WebView page to finish loading")
                    .that(pageLoaded.await(WEBVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Set<Integer> rendererPids = webViewRendererPids(providerPackageName);
            rendererPids.removeAll(existingRendererPids);
            assertWithMessage("expected exactly one new renderer for " + providerPackageName)
                    .that(rendererPids).hasSize(1);
            int rendererPid = rendererPids.iterator().next();
            String cgroupPath = processCgroupPath(rendererPid);
            assertWithMessage("expected renderer cgroup to exist: " + cgroupPath)
                    .that(cgroupExists(cgroupPath)).isTrue();

            CountDownLatch rendererGone = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> {
                WebView webView = activity.getWebView();
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean onRenderProcessGone(
                            WebView view, RenderProcessGoneDetail detail) {
                        rendererGone.countDown();
                        return true;
                    }
                });
                webView.loadUrl("chrome://kill");
            });
            assertWithMessage("expected chrome://kill to terminate the WebView renderer")
                    .that(rendererGone.await(WEBVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertWithMessage("expected renderer cgroup to be removed: " + cgroupPath)
                    .that(waitForCgroupRemoval(cgroupPath)).isTrue();
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    private static Set<Integer> webViewRendererPids(String providerPackageName)
            throws IOException {
        Set<Integer> result = new HashSet<>();
        String processNamePrefix = providerPackageName + ":sandboxed_process";
        for (String line : shell("ps -A -o PID,NAME:256").split("\\R")) {
            String[] fields = line.trim().split("\\s+", 2);
            if (fields.length == 2 && fields[1].startsWith(processNamePrefix)) {
                result.add(Integer.parseInt(fields[0]));
            }
        }
        return result;
    }

    private static String processCgroupPath(int pid) throws IOException {
        String cgroups = shell("cat /proc/" + pid + "/cgroup");
        String expectedPathPattern = "/apps/uid_[0-9]+/pid_" + pid;
        for (String line : cgroups.split("\\R")) {
            if (line.startsWith("0::")) {
                String relativePath = line.substring(3);
                if (relativePath.matches(expectedPathPattern)) {
                    return "/sys/fs/cgroup" + relativePath;
                }
            }
        }
        throw new AssertionError("missing process cgroup for pid " + pid + ":\n" + cgroups);
    }

    private static boolean waitForCgroupRemoval(String path) throws IOException {
        long deadline = SystemClock.elapsedRealtime()
                + TimeUnit.SECONDS.toMillis(WEBVIEW_TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!cgroupExists(path)) {
                return true;
            }
            SystemClock.sleep(100);
        }
        return !cgroupExists(path);
    }

    private static boolean cgroupExists(String path) throws IOException {
        return path.equals(shell("ls -d " + path).trim());
    }

    // UiAutomation runs commands as shell, which can read cross-UID proc and cgroup paths on
    // user builds.
    private static String shell(String command) throws IOException {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void runtimeMemoryAccountingCheck() {
        SecureSpawnSmapsCheck.AndroidRuntimeSmaps result = SecureSpawnSmapsCheck.run();
        Log.i(TAG, "runtimeMemoryAccountingCheck\n" + result);
        assertWithMessage(failureMessage("expected sections > 0", result))
                .that(result.sections()).isGreaterThan(0);
        assertWithMessage(failureMessage("expected androidRuntimeSections > 0", result))
                .that(result.androidRuntimeSections()).isGreaterThan(0);
        assertWithMessage(failureMessage("expected isWithinMemoryBounds == true", result))
                .that(result.isWithinMemoryBounds()).isTrue();
    }

    @Test
    public void fdStateCheck() {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        SecureSpawnFileDescriptorCheck.FileDescriptorState result =
                SecureSpawnFileDescriptorCheck.run();
        Log.i(TAG, "fdStateCheck\n" + processState + "\n" + result);
        assertProcessState(processState);
        assertDescriptorState("framework", result.framework(), result);
        assertDescriptorState("sharedMemory", result.sharedMemory(), result);
        assertWithMessage(failureMessage(
                "expected detached mount ID regression == false", result))
                .that(result.hasDetachedMountIdRegression()).isFalse();
    }

    @Test
    public void hiddenApiEnforcementCheck() {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        SecureSpawnHiddenApiCheck.HiddenApiEnforcement result =
                SecureSpawnHiddenApiCheck.run(processState.execSpawned());
        Log.i(TAG, "hiddenApiEnforcementCheck\n" + result);
        assertProcessState(processState);
        assertWithMessage(failureMessage(
                "expected hidden API execSpawned to match process execSpawned", result))
                .that(result.execSpawned())
                .isEqualTo(processState.execSpawned());
        assertWithMessage(failureMessage("expected objectShadowFieldsHidden == true", result))
                .that(result.objectShadowFieldsHidden()).isTrue();
    }

    @Test
    public void testApiCompatDefaultCheck() {
        testApiCompatCheck("testApiCompatDefaultCheck", false);
    }

    @Test
    public void testApiCompatDisabledCheck() {
        testApiCompatCheck("testApiCompatDisabledCheck", false);
    }

    @Test
    public void testApiCompatEnabledCheck() {
        testApiCompatCheck("testApiCompatEnabledCheck", true);
    }

    @Test
    public void profileableFromShellDumpableCheck() {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        SecureSpawnDumpableCheck.DumpableState result =
                SecureSpawnDumpableCheck.run(processState.execSpawned());
        Log.i(TAG, "profileableFromShellDumpableCheck\n" + result);
        assertProcessState(processState);
        assertWithMessage(failureMessage(
                "expected dumpable check execSpawned to match process execSpawned", result))
                .that(result.execSpawned())
                .isEqualTo(processState.execSpawned());
        assertWithMessage(failureMessage("expected isDumpable == true", result))
                .that(result.isDumpable()).isTrue();
    }

    @Test
    public void acyclicReflectiveDumpCheck() {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        SecureSpawnReflectiveDumpCheck.AcyclicReflectiveDump result =
                SecureSpawnReflectiveDumpCheck.run(processState.execSpawned());
        Log.i(TAG, "acyclicReflectiveDumpCheck\n" + result);
        assertProcessState(processState);
        assertWithMessage(failureMessage(
                "expected reflective dump execSpawned to match process execSpawned", result))
                .that(result.execSpawned())
                .isEqualTo(processState.execSpawned());
        assertWithMessage(failureMessage("expected threadTid > 0", result))
                .that(result.threadTid()).isGreaterThan(0);
        assertWithMessage(failureMessage("expected threadTid != Process.myTid()", result))
                .that(result.threadTid()).isNotEqualTo(Process.myTid());
        assertWithMessage(failureMessage("expected fixtureDepth > 1", result))
                .that(result.fixtureDepth()).isGreaterThan(1);
        assertWithMessage(failureMessage("expected completed == true", result))
                .that(result.completed()).isTrue();
        assertWithMessage(failureMessage("expected resultLength > 0", result))
                .that(result.resultLength()).isGreaterThan(0);
    }

    @Test
    public void cmdlinePackageNameReaderCheck() throws Exception {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        SecureSpawnCmdlineCheck.CmdlinePackageNameRead result =
                SecureSpawnCmdlineCheck.run(processState.execSpawned());
        Log.i(TAG, "cmdlinePackageNameReaderCheck\n" + result);
        assertProcessState(processState);
        assertWithMessage(failureMessage(
                "expected cmdline check execSpawned to match process execSpawned", result))
                .that(result.execSpawned())
                .isEqualTo(processState.execSpawned());
        assertWithMessage(failureMessage("expected first cmdline string to be package name",
                result)).that(result.firstNulOffset()).isEqualTo(PACKAGE_NAME.length());
        assertWithMessage(failureMessage("expected parsed package name", result))
                .that(result.parsedPackageName()).isEqualTo(PACKAGE_NAME);
        assertWithMessage(failureMessage("expected zero-filled argv padding after argv[0]",
                result)).that(result.nonZeroBytesAfterFirstNul()).isEqualTo(0);
        assertWithMessage(failureMessage("expected 256 byte package buffer not to"
                + " overflow", result)).that(result.wouldOverflow()).isFalse();
        assertWithMessage(failureMessage("expected parser index to fit 256 byte"
                + " package buffer", result)).that(result.maxIndex()).isAtMost(256);
    }

    @Test
    public void mediaProfilesCwdIndependenceCheck() {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // The app's own data directory always exists and is never "/", so chdir into it forces a
        // non-root cwd before the first CamcorderProfile use in this fresh process.
        String chdirTarget = context.getDataDir().getAbsolutePath();
        SecureSpawnMediaProfilesCheck.MediaProfilesCwd result =
                SecureSpawnMediaProfilesCheck.run(processState.execSpawned(), chdirTarget);
        Log.i(TAG, "mediaProfilesCwdIndependenceCheck\n" + result);
        assertProcessState(processState);
        assertWithMessage(failureMessage(
                "expected media profiles check execSpawned to match process execSpawned", result))
                .that(result.execSpawned())
                .isEqualTo(processState.execSpawned());
        assertWithMessage(failureMessage("expected chdir off \"/\" to succeed", result))
                .that(result.changedDirectoryOffRoot()).isTrue();
        // Non-zero legacy id required: the MediaProfiles default fallback instance only ever
        // defines camera id 0, so profiles on a non-zero id are what prove the device XML loaded.
        assertWithMessage(failureMessage(
                "expected a front-facing camera at a non-default legacy id (>= 1)", result))
                .that(result.frontCameraId()).isAtLeast(1);
        assertWithMessage(failureMessage(
                "expected front-camera camcorder profiles to load after chdir off \"/\""
                        + " (MediaProfiles must not fall back to the default instance)", result))
                .that(result.frontProfilesLoaded()).isTrue();
    }

    private static void testApiCompatCheck(String methodName, boolean expectedAccessAllowed) {
        SecureSpawnCheck.ProcessState processState = SecureSpawnCheck.processState();
        SecureSpawnTestApiCompatCheck.TestApiCompat result =
                SecureSpawnTestApiCompatCheck.run(processState.execSpawned());
        Log.i(TAG, methodName + "\n" + result);
        assertProcessState(processState);
        assertWithMessage(failureMessage(
                "expected test API compat execSpawned to match process execSpawned", result))
                .that(result.execSpawned())
                .isEqualTo(processState.execSpawned());
        SecureSpawnTestApiCompatCheck.AccessOutcome expectedOutcome = expectedAccessAllowed
                ? SecureSpawnTestApiCompatCheck.AccessOutcome.ACCESS_ALLOWED
                : SecureSpawnTestApiCompatCheck.AccessOutcome.ACCESS_DENIED;
        assertWithMessage(failureMessage("expected test API access outcome == "
                + expectedOutcome, result)).that(result.accessResult().outcome())
                .isEqualTo(expectedOutcome);
    }

    private static void assertProcessState(SecureSpawnCheck.ProcessState result) {
        assertWithMessage(failureMessage("expected pid > 0", result))
                .that(result.pid()).isGreaterThan(0);
        assertWithMessage(failureMessage("expected tid > 0", result))
                .that(result.tid()).isGreaterThan(0);
    }

    private static void assertDescriptorState(
            String name,
            SecureSpawnFileDescriptorCheck.DescriptorState state,
            SecureSpawnFileDescriptorCheck.FileDescriptorState result) {
        if (!state.present()) {
            return;
        }
        assertWithMessage(failureMessage("expected " + name + " fd >= 0", result))
                .that(state.fd()).isAtLeast(0);
        assertWithMessage(failureMessage("expected " + name + " mount ID > 0", result))
                .that(state.mountId()).isGreaterThan(0);
    }

    private static String failureMessage(String expectation, Object result) {
        return expectation + "\n" + result;
    }
}
