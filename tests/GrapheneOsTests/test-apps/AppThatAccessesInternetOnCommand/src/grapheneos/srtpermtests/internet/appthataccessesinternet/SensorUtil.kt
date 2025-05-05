package grapheneos.srtpermtests.internet.appthataccessesinternet

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object SensorUtil {
    private const val TAG = "SensorUtil"

    @JvmStatic
    fun getSensorEvent(
        sensorManager: SensorManager,
        sensor: Sensor,
        timeoutMillis: Long,
    ): SensorEvent? {
        return runBlocking {
            getSensorEventSuspend(sensorManager, sensor, timeoutMillis)
        }
    }

    suspend fun getSensorEventSuspend(
        sensorManager: SensorManager,
        sensor: Sensor,
        timeoutMillis: Long
    ): SensorEvent? {
        return withTimeoutOrNull(timeoutMillis) {
            var sensorEventListener : SensorEventListener? = null
            try {
                suspendCancellableCoroutine { cont ->
                    sensorEventListener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            Log.d(TAG, "onSensorChanged: ${event?.values?.asList()}")
                            cont.resume(event)
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }
                    sensorManager.registerListener(
                        sensorEventListener,
                        sensor,
                        SensorManager.SENSOR_DELAY_FASTEST
                    )
                    Log.d(TAG, "registered sensor listener")
                    // cont.invokeOnCancellation doesn't seem to be called on a success?
                }
            } finally {
                sensorEventListener?.let {
                    Log.d(TAG, "unregistered sensor listener")
                    sensorManager.unregisterListener(it)
                }
            }
        }
    }
}
