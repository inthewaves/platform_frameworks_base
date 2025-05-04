package grapheneos.srtpermtests.internet.appthataccessesinternet

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import java.net.InetAddress

class AccessInternetOnCommand2 : Service() {
    private val mBinder: IAccessInternetOnCommand.Stub = object : Stub() {
        fun accessInternet() {
            InetAddress.getByName("grapheneos.org")
        }
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder {
        return mBinder
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        return true
    }
}
