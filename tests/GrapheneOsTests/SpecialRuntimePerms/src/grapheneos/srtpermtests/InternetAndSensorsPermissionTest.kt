package grapheneos.srtpermtests

import android.Manifest
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import grapheneos.test.common.notifications.GtsNotificationListenerHelperRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import com.android.internal.messages.nano.SystemMessageProto
import grapheneos.srtpermtests.internet.appthataccessesinternet.IAccessInternetOnCommand
import grapheneos.srtpermtests.packageinstaller.TestApks
import grapheneos.test.common.DeadObjectExceptionRetryRule
import grapheneos.test.common.notifications.GtsNotificationListenerServiceUtils
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val TEST_APP_PKG = TestApks.appThatAccessesInternet.packageName
private val TEST_APP_SERVICE = "$TEST_APP_PKG.AccessInternetOnCommand"

private const val SENSORS_TEST_TIMEOUT_MILLIS = 4_000L

/**
 * Based on
 * packages/modules/Permission/tests/cts/permission/src/android/permission/cts/LocationAccessCheckTest.java
 */
@RunWith(AndroidJUnit4::class)
class InternetAndSensorsPermissionTest {

    val mInstrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val mPackageManager: PackageManager = mContext.packageManager
    val uiDevice: UiDevice = UiDevice.getInstance(mInstrumentation)

    companion object {

        private val mContext: Context = androidx.test.InstrumentationRegistry.getTargetContext();
        private var serviceConn: ServiceConnection? = null
        private var accessor: IAccessInternetOnCommand? = null

        // maybe refactor into a JUnit ClassRule if more tests of this nature are needed
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            installBackgroundAccessApp()
            // Might be needed to allow test app to do internet calls in Service.
            // Note: Commenting this out alone seems to result in all tests still passing.
            // Removing this and adding Context.BIND_NOT_FOREGROUND to the service binding,
            // will cause some of the internet granted tests to fail.
            setIdleAllowlist(true)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            setIdleAllowlist(false)
            uninstallBackgroundAccessApp()
            unbindService()
        }

        private fun installBackgroundAccessApp() {
            val output = SystemUtil.runShellCommandOrThrow(
                // -g means grant all runtime permissions
                "pm install -r -g " + TestApks.appThatAccessesInternet.apkPath
            )
            assertTrue(output.contains("Success"))
        }

        private fun uninstallBackgroundAccessApp() {
            val output = SystemUtil.runShellCommandOrThrow(
                "pm uninstall $TEST_APP_PKG"
            )
            assertTrue(output.contains("Success"))
        }

        private fun wakeUpAndDismissKeyguard() {
            SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
            SystemUtil.runShellCommand("wm dismiss-keyguard")
        }

        private fun setIdleAllowlist(enabled: Boolean) {
            val prefix = if (enabled) "+" else "-"
            val command = "cmd deviceidle whitelist $prefix$TEST_APP_PKG"
            SystemUtil.runShellCommand(command)
        }

        private suspend fun bindService(): IAccessInternetOnCommand {
            if (serviceConn != null && accessor != null) {
                return accessor!!
            }

            return suspendCancellableCoroutine { cont ->
                serviceConn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val acc = IAccessInternetOnCommand.Stub.asInterface(service)
                        accessor = acc
                        cont.resume(acc) {
                            unbindService()
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        serviceConn = null
                        accessor = null
                        cont.cancel()
                    }
                }
                val intent = Intent()
                intent.component = ComponentName(TEST_APP_PKG, TEST_APP_SERVICE)
                mContext.bindService(
                    intent,
                    serviceConn!!,
                    // adding Context.BIND_NOT_FOREGROUND will make test app service unable to
                    // get sensor readings
                    Context.BIND_AUTO_CREATE
                )
            }
        }

        private fun unbindService() {
            serviceConn?.let {
                mContext.unbindService(it)
                serviceConn = null
            }
            accessor = null
        }
    }

    @get:Rule
    val ctsNotificationListenerHelper = GtsNotificationListenerHelperRule(mContext)

    @get:Rule
    val deadObjectRetryRule = DeadObjectExceptionRetryRule(retryCount = 2)

    @Before
    fun beforeEachTest() {
        mInstrumentation.uiAutomation.grantRuntimePermission(
            TEST_APP_PKG,
            Manifest.permission.INTERNET
        )
        mInstrumentation.uiAutomation.grantRuntimePermission(
            TEST_APP_PKG,
            Manifest.permission.OTHER_SENSORS
        )
        wakeUpAndDismissKeyguard()
    }

    @After
    fun afterEachTest() {
        unbindService()
    }

    @Test
    fun internet_granted_resolve_name_successful() = runTest {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            mPackageManager.checkPermission(
                Manifest.permission.INTERNET,
                TEST_APP_PKG
            ),
            "expected INTERNET to be granted"
        )
        val acc = bindService()
        acc.accessInternet()
    }

    @Test
    fun internet_granted_connectivity_manager_methods_show_connected() = runTest {
        val acc = bindService()
        val isConnected = acc.isConnected()
        assertTrue(isConnected)
    }

    @Test
    fun internet_revoked_resolve_name_throws_exception_like_no_internet() = runTest {
        Assume.assumeTrue(
            "network should be available",
            try {
                InetAddress.getByName("grapheneos.org")
                true
            } catch (e: UnknownHostException) {
                false
            } catch (e: SecurityException) {
                false
            }
        )

        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TEST_APP_PKG,
            Manifest.permission.INTERNET
        )

        val acc = bindService()
        // We expect a SecurityException here, since this is an RPC call. The message should contain
        // the actual exception thrown in the test app.
        val exception = assertFailsWith<SecurityException> { acc.accessInternet() }
        val msg = assertNotNull(exception.message)
        // Note that in AOSP, an app will throw a SecurityException if it doesn't have INTERNET
        // permission. In GrapheneOS, revoking the INTERNET permission will cause the app to be
        // treated as having no internet access
        assertContains(
            msg,
            "java.net.UnknownHostException: Unable to resolve host \"grapheneos.org\": No address associated with hostname"
        )
    }

    @Test
    fun internet_revoked_connectivity_manager_methods_show_not_connected() = runTest {
        val cm = mContext.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        Assume.assumeTrue(network != null)
        val caps = cm.getNetworkCapabilities(network)
        Assume.assumeTrue(caps != null)
        Assume.assumeTrue(caps!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TEST_APP_PKG,
            Manifest.permission.INTERNET
        )

        val acc = bindService()
        val isConnected: Boolean = acc.isConnected()
        assertFalse(isConnected)
    }

    @Test
    fun sensors_granted_get_success() = runTest {
        val acc = bindService()
        val sensorInfoPresent: Boolean = acc.getSensorInfo(SENSORS_TEST_TIMEOUT_MILLIS)
        assertTrue(sensorInfoPresent)
    }

    @Test
    fun sensors_denied_get_fail_with_notif() = runTest {
        GtsNotificationListenerServiceUtils.cancelNotification(
            "android",
            SystemMessageProto.SystemMessage.NOTE_MISSING_PERMISSION_OTHER_SENSORS
        )

        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TEST_APP_PKG,
            Manifest.permission.OTHER_SENSORS
        )

        val acc = bindService()
        val sensorInfoPresent = acc.getSensorInfo(SENSORS_TEST_TIMEOUT_MILLIS)
        assertFalse(sensorInfoPresent, "expected getSensorInfo to fail/timeout when OTHER_SENSORS denied")

        // note: the notif isn't meant to ben show if OTHER_SENSORS explicitly denied by user, but
        // mInstrumentation.uiAutomation.revokeRuntimePermission doesn't seem to treat it that way
        val notif = GtsNotificationListenerServiceUtils.getNotificationForPackageAndId(
            "android",
            SystemMessageProto.SystemMessage.NOTE_MISSING_PERMISSION_OTHER_SENSORS,
            false
        )
        assertNotNull(
            notif,
            "missing notification for when sensors access denied by OTHER_SENSORS permission"
        )
    }
}
