package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import android.util.Log;

import java.net.InetAddress;

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
