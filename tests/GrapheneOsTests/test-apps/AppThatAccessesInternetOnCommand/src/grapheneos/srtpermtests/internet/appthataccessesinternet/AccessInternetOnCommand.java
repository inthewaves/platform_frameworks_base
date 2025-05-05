package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import android.util.Log;

import java.net.InetAddress;
import java.util.List;

public class AccessInternetOnCommand extends Service {
    private static final String TAG = AccessInternetOnCommand.class.getSimpleName();

    private final IAccessInternetOnCommand.Stub mBinder = new IAccessInternetOnCommand.Stub() {
        @Override
        public void accessInternet() {
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
            final var cm = AccessInternetOnCommand.this.getSystemService(ConnectivityManager.class);
            final var network = cm.getActiveNetwork();
            if (network == null) return false;
            final var caps = cm.getNetworkCapabilities(network);
            if (caps == null) return false;
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        @Override
        public boolean getSensorInfo() {
            /*
            final var sm = AccessInternetOnCommand.this.getSystemService(SensorManager.class);
            var sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (sensor == null) {
                return false;
            }
            var sensorEvent = SensorUtil.getSensorEvent(sm, sensor);
            Log.d(TAG, "sensorEvent=" + sensorEvent);
            return sensorEvent != null && sensorEvent.values.length > 0;
            */

            // Apparently sensors don't report anything if it's from this service...
            var intent = new Intent(AccessInternetOnCommand.this, SensorService.class);
            var bindResult = bindService(
                    intent,
                    new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder service) {
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                        }
                    },
                    Context.BIND_AUTO_CREATE
            );
            Log.d(TAG, "bind result=" + bindResult);
            startForegroundService(intent);

            var latestEvent = SensorService.getLatestEvent();
            return latestEvent != null && latestEvent.values.length > 0;
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
