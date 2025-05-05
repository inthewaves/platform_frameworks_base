package grapheneos.srtpermtests.internet

import android.Manifest
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.UserHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import grapheneos.srtpermtests.internet.appthataccessesinternet.IAccessInternetOnCommand
import grapheneos.srtpermtests.packageinstaller.TestApks
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

private val TEST_APP_SERVICE =
    TestApks.appThatAccessesInternet.packageName + ".AccessInternetOnCommand"

/**
 * Based on
 * packages/modules/Permission/tests/cts/permission/src/android/permission/cts/LocationAccessCheckTest.java
 */
@RunWith(AndroidJUnit4::class)
class InternetPermissionTest {

    val mInstrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    val mPackageManager = mContext.packageManager
    val uiDevice = UiDevice.getInstance(mInstrumentation)

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
            setIdleAllowlist(true)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            setIdleAllowlist(false)
            val output = SystemUtil.runShellCommandOrThrow(
                "pm uninstall " + TestApks.appThatAccessesInternet.packageName
            )
            assertTrue(output.contains("Success"))

            unbindService()
        }

        private fun installBackgroundAccessApp() {
            val output = SystemUtil.runShellCommandOrThrow(
                // -g means grant all runtime permissions
                "pm install -r -g " + TestApks.appThatAccessesInternet.apkPath
            )
            Assert.assertTrue(output.contains("Success"))
        }


        private fun wakeUpAndDismissKeyguard() {
            SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
            SystemUtil.runShellCommand("wm dismiss-keyguard")
        }

        private fun setIdleAllowlist(enabled: Boolean) {
            val prefix = if (enabled) "+" else "-"
            val command = "cmd deviceidle whitelist $prefix${TestApks.appThatAccessesInternet.packageName}"
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
                    }
                }
                val intent = Intent()
                intent.component = ComponentName(
                    TestApks.appThatAccessesInternet.packageName,
                    TEST_APP_SERVICE
                )
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
        SystemUtil.runWithShellPermissionIdentity(
            {
                val user = UserHandle.of(mContext.userId)
                mPackageManager.grantRuntimePermission(
                    TestApks.appThatAccessesInternet.packageName,
                    Manifest.permission.INTERNET,
                    user
                )
            },
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
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
                TestApks.appThatAccessesInternet.packageName
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
        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TestApks.appThatAccessesInternet.packageName,
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
        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TestApks.appThatAccessesInternet.packageName,
            Manifest.permission.INTERNET
        )

        val acc = bindService()
        val isConnected = acc.isConnected()
        assert(!isConnected)
    }
}