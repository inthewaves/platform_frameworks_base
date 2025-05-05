package grapheneos.srtpermtests.internet.appthataccessesinternet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import kotlinx.coroutines.launch

private const val TAG = "InternetJobService"
private const val NOTIF_CHANNEL_ID = "channel1"


class InternetJobService : Service() {

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
        // startForeground(1, notification)


        scope.launch {
            // onStartJob(null)
        }
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