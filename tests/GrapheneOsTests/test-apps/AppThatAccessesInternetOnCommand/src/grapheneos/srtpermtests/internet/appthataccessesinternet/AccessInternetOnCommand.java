package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.net.InetAddress;

public class AccessInternetOnCommand extends Service {
    private static final String TAG = AccessInternetOnCommand.class.getSimpleName();

    private final IAccessInternetOnCommand.Stub mBinder = new IAccessInternetOnCommand.Stub() {
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
