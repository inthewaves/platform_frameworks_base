package grapheneos.srtpermtests.internet.appthataccessesinternet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.job.JobParameters
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.os.IBinder
import android.util.Log
import android.os.Process;
import java.io.IOException

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "SensorService"
private const val NOTIF_CHANNEL_ID = "channel1"


class SensorService : Service() {
    companion object {
        private val _channel: Channel<SensorEvent> = Channel(Channel.RENDEZVOUS)
        val channel: ReceiveChannel<SensorEvent> = _channel

        @JvmStatic
        fun getLatestEvent() = runBlocking {
            channel.receive()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel("Service destroyed")
    }

    private fun createNotificationChannel() {
        val channelName = "Foreground service notification"
        val chan = NotificationChannel(NOTIF_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH)
        //chan.lightColor = Color.BLUE
        //chan.importance = NotificationManager.IMPORTANCE_NONE
        //chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "service created: " + this + " in " + Process.myPid());

        createNotificationChannel()
        val notification = Notification.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()
        startForeground(1, notification)


        scope.launch {
            // onStartJob(null)
            Log.d(TAG, "scope.launch")
            val sm = getSystemService(SensorManager::class.java)
            val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) {
                val sensorValue = SensorUtil.getSensorEventSuspend(sm, sensor)
                Log.d(TAG, "sensorValue=$sensorValue")
                sensorValue?.let { _channel.trySend(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    fun onStartJob(params: JobParameters?): Boolean {
        val connectivityManager: ConnectivityManager =
            getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork
        Log.d(TAG, "network=$network")
        if (network != null) {
            try {
                val url = URL("https://grapheneos.org")
                val conn = network.openConnection(url) as HttpsURLConnection
                try {
                    conn.requestMethod = "GET"
                    val responseCode = conn.responseCode
                    Log.d(TAG, "responseCode=$responseCode")
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                Log.d(TAG, "error", e)
            }
        }

        return false
    }

    fun onStopJob(params: JobParameters?): Boolean {
        return false;
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}