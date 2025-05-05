package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.net.InetAddress;

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
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }
}
