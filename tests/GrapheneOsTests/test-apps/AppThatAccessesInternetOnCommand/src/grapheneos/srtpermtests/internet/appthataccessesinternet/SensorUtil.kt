package grapheneos.srtpermtests.internet.appthataccessesinternet

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

object SensorUtil {
    private const val TAG = "SensorUtil"

    @JvmStatic
    fun getSensorEvent(sensorManager: SensorManager, sensor: Sensor): SensorEvent? {
        return runBlocking {
            withTimeout(20_000L) {
                getSensorEventSuspend(sensorManager, sensor)
            }
        }
    }

    suspend fun getSensorEventSuspend(sensorManager: SensorManager, sensor: Sensor): SensorEvent? {
        return withTimeout(20_000L) {
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
                    SensorManager.SENSOR_DELAY_FASTEST
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
