package grapheneos.srtpermtests

import grapheneos.test.common.SensorsSettingsUtil

import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Test

class SensorsSettingsTest {

    lateinit var mInstrumentation: Instrumentation
    lateinit var mContext: Context
    lateinit var mPackageManager: PackageManager
    lateinit var mUiDevice: UiDevice

    @Before
    fun setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation()
        mContext = mInstrumentation.getContext()
        mPackageManager = mContext.getPackageManager()
        mUiDevice = UiDevice.getInstance(mInstrumentation)
    }

    /**
     * Based on failing CTS test
     * cts/tests/tests/content/src/android/content/pm/cts/PackageManagerTest.java#testGetPermissionInfo
     */
    @Test
    fun sensors_setting_on() {
        //SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, false) {

        //}
    }
}
