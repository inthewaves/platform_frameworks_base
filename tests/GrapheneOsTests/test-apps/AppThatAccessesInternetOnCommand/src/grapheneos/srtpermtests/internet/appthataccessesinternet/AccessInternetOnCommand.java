package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.net.InetAddress;
import java.util.List;

public class AccessInternetOnCommand extends Service {
    private static final String NOTIF_CHANNEL_ID = "AccessInternetOnCommand_channel1";

    private static final String TAG = AccessInternetOnCommand.class.getSimpleName();

    private final IAccessInternetOnCommand.Stub mBinder = new IAccessInternetOnCommand.Stub() {
        @Override
        public void accessInternet() {
            Log.d(TAG, "accessInternet, pid " + Process.myPid());
            var result = AccessInternetOnCommand.this.getPackageManager().checkPermission(
                    Manifest.permission.INTERNET,
                    AccessInternetOnCommand.this.getPackageName()
            );
            Log.d(TAG, "permissions result=" + result);
            try {
                InetAddress.getByName("grapheneos.org");
            } catch (Exception e) {
                throw new SecurityException(e);
            }
        }

        @Override
        public boolean isConnected() {
            Log.d(TAG, "isConnected, pid " + Process.myPid());
            final var cm = AccessInternetOnCommand.this.getSystemService(ConnectivityManager.class);
            final var network = cm.getActiveNetwork();
            if (network == null) return false;
            final var caps = cm.getNetworkCapabilities(network);
            if (caps == null) return false;
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        @Override
        public boolean getSensorInfo(long timeoutMillis) {
            Log.d(TAG, "getSensorInfo, pid " + Process.myPid());
            final var sm = AccessInternetOnCommand.this.getSystemService(SensorManager.class);
            var sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (sensor == null) {
                return false;
            }
            var sensorEvent = SensorUtil.getSensorEvent(sm, sensor, timeoutMillis);
            Log.d(TAG, "sensorEvent=" + sensorEvent);
            return sensorEvent != null && sensorEvent.values.length > 0;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate, pid " + Process.myPid());

        /*
        var channelName = "Foreground service notification";
        var chan = new NotificationChannel(NOTIF_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
        var service = getSystemService(NotificationManager.class);
        service.createNotificationChannel(chan);

        var notification = new Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_secure)
                .build();
        // If not started as foreground service, sensors will not update at all
        startForeground(1, notification);

         */
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }
}
