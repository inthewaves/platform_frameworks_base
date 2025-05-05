package grapheneos.srtpermtests.internet

import android.Manifest
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.UserHandle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import grapheneos.srtpermtests.internet.appthataccessesinternet.IAccessInternetOnCommand
import grapheneos.srtpermtests.packageinstaller.TestApks
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith


private val TEST_APP_PKG = TestApks.appThatAccessesInternet.packageName
private val TEST_APP_SERVICE =
    TEST_APP_PKG + ".AccessInternetOnCommand"

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
        @JvmField
        val mContext: Context = androidx.test.InstrumentationRegistry.getTargetContext();
        @JvmField
        var serviceConn: ServiceConnection? = null
        @JvmField
        var accessor: IAccessInternetOnCommand? = null

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            installBackgroundAccessApp()
            // Required to allow test app to do internet calls in background service
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
            val command = "cmd deviceidle whitelist $prefix${TEST_APP_PKG}"
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
                    Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND
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
        assert(isConnected)
    }

    @Test
    fun internet_revoked_resolve_name_throws_exception_like_no_internet() = runTest {
        assumeTrue(
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
        assumeTrue(network != null)
        val caps = cm.getNetworkCapabilities(network)
        assumeTrue(caps != null)
        assumeTrue(caps!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

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
        val sensorInfoPresent: Boolean = acc.getSensorInfo(8_000)
        Log.d("InternetAndSensorsPermissionTest", "sensorInfoPresent=$sensorInfoPresent")
        assertTrue(sensorInfoPresent)
    }

    @Test
    fun sensors_denied_get_fail() = runTest {
        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TEST_APP_PKG,
            Manifest.permission.OTHER_SENSORS
        )

        val acc = bindService()
        val sensorInfoPresent = acc.getSensorInfo(4_000)
        assertFalse(sensorInfoPresent)
    }

    @Test
    fun sensors_self_test() {
        val sm = mContext.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        assertNotNull(sensor)

        //val sensorEvent = SensorUtil.getSensorEvent(sm, sensor)
        //assertNotNull(sensorEvent)
        //assertTrue(sensorEvent.values.isNotEmpty())
    }
}

object SensorUtil {
    private const val TAG = "SensorUtil"

    fun getSensorEvent(sensorManager: SensorManager, sensor: Sensor): SensorEvent? {
        return runBlocking {
            withTimeout(20_000L) {
                suspendCancellableCoroutine { cont ->
                    val sensorEventListener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            Log.d(TAG, "onSensorChanged: ${event?.values?.asList()}")
                            cont.resume(event)
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }
                    sensorManager.registerListener(
                        sensorEventListener,
                        sensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                    Log.d(TAG, "registered sensor listener")
                    cont.invokeOnCancellation {
                        Log.d(TAG, "unregistered sensor listener")
                        sensorManager.unregisterListener(sensorEventListener)
                    }
                }
            }
        }
    }
}
