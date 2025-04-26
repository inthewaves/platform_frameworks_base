package grapheneos.test.common

import android.app.Instrumentation
import android.util.Log
import com.android.compatibility.common.util.SystemUtil

private const val TAG = "SensorsSettingsUtil"

object SensorsSettingsUtil {
    /**
     * Unable to access [Settings.Secure.AUTO_GRANT_OTHER_SENSORS_PERMISSION] directly due to it
     * being a @hide API
     */
    private const val AUTO_GRANT_SETTING = "auto_grant_OTHER_SENSORS_perm"

    fun interface ThrowableRunnable {
        @Throws(Exception::class)
        fun run()
    }

    @JvmStatic
    fun withAutoGrantSensorSetting(
        instrumentation: Instrumentation,
        settingForScope: Boolean,
        scope: ThrowableRunnable
    ) {
        val prevValue = getAutoGrantSensorsSetting(instrumentation)
        try {
            setAutoGrantSensorsSetting(instrumentation, settingForScope)
            scope.run()
        } finally {
            setAutoGrantSensorsSetting(instrumentation, prevValue)
        }
    }

    @JvmStatic
    fun setAutoGrantSensorsSetting(instrumentation: Instrumentation, isEnabled: Boolean?) {
        if (isEnabled == null) {
            SystemUtil.runShellCommand(
                instrumentation,
                "settings delete secure $AUTO_GRANT_SETTING"
            )
        } else {
            val value = if (isEnabled) "1" else "0"
            SystemUtil.runShellCommand(
                instrumentation,
                "settings put secure $AUTO_GRANT_SETTING $value"
            )
        }
    }

    @JvmStatic
    fun getAutoGrantSensorsSetting(instrumentation: Instrumentation): Boolean? {
        val returnValue = SystemUtil.runShellCommand(
            instrumentation,
            "settings get secure $AUTO_GRANT_SETTING"
        ).trim()
        Log.d(TAG, "actual returnValue is <$returnValue>")
        if (returnValue == "null") {
            return null
        }
        return returnValue == "1" || returnValue == "true"
    }
}