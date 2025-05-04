package grapheneos.srtpermtests.internet

import android.Manifest
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
// import android.platform.test.rule.ScreenRecordRule.ScreenRecord
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import grapheneos.srtpermtests.internet.appthataccessesinternet.IAccessInternetOnCommand
import grapheneos.srtpermtests.packageinstaller.TestApks
import java.net.UnknownHostException
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
// @RunWith(AndroidJUnit4::class)
// @RunWith(AndroidJUnit4::class)
//@RunWith(AndroidJUnit4::class)
//@AppModeFull(
//    reason = ("Cannot set system settings as instant app. Also we never show a location "
//            + "access check notification for instant apps.")
//)
// @ScreenRecord
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
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
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
            // Wait for user sensitive to be updated, which is checked by LocationAccessCheck.
            Thread.sleep(5000)
        }


        private fun wakeUpAndDismissKeyguard() {
            SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
            SystemUtil.runShellCommand("wm dismiss-keyguard")
        }

        private fun bindService() {
            if (serviceConn != null && accessor != null) {
                return
            }

            serviceConn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    accessor = IAccessInternetOnCommand.Stub.asInterface(service)
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
        /*
        mInstrumentation.uiAutomation.grantRuntimePermission(
            TestApks.appThatAccessesInternet.packageName,
            Manifest.permission.INTERNET
        )

         */
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
        bindService()
    }

    @After
    fun afterEachTest() {
        unbindService()
    }

    private inline fun eventually(block: () -> Unit) {
        var attempts = 0
        while (true) {
            try {
                block()
                return
            } catch (e: Throwable) {
                if (attempts > 50) {
                    throw e
                }
                attempts++
                Thread.sleep(500)
            }
        }
    }

    @Test
    fun internet_granted_resolve_name_successful() {
        /*
        mInstrumentation.uiAutomation.grantRuntimePermission(
            TestApks.appThatAccessesInternet.packageName,
            Manifest.permission.INTERNET
        )

         */

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            mPackageManager.checkPermission(
                Manifest.permission.INTERNET,
                TestApks.appThatAccessesInternet.packageName
            ),
            "expected INTERNET to be granted"
        )

        unbindService()
        // Rebind because revoking runtime permissions will stop the app
        bindService()

        eventually { assertNotNull(accessor) }

        accessor!!.accessInternet()
    }

    @Test
    fun internet_revoked_resolve_name_throws() {
        mInstrumentation.uiAutomation.revokeRuntimePermission(
            TestApks.appThatAccessesInternet.packageName,
            Manifest.permission.INTERNET
        )

        // Rebind because revoking runtime permissions will stop the app
        bindService()

        eventually { assertNotNull(accessor) }
        val accessor = accessor!!
        val exception = assertFailsWith<SecurityException> { accessor.accessInternet() }
        val msg = assertNotNull(exception.message)
        assertContains(
            msg,
            "java.net.UnknownHostException: Unable to resolve host"
        )
    }
}