package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.net.InetAddress;

public class AccessInternetOnCommand extends Service {
    private static final String TAG = AccessInternetOnCommand.class.getSimpleName();

    private final Handler mHandler = new Handler(Looper.myLooper());

    private final IAccessInternetOnCommand.Stub mBinder = new IAccessInternetOnCommand.Stub() {
        public void accessInternet() throws RemoteException {
            var result = AccessInternetOnCommand.this.getPackageManager().checkPermission(
                    Manifest.permission.INTERNET,
                    AccessInternetOnCommand.this.getPackageName()
            );
            Log.d(TAG, "permissions result=" + result);

            /*
            var sch = AccessInternetOnCommand.this.getSystemService(JobScheduler.class);
            var jobInfo = new JobInfo.Builder(1, new ComponentName(AccessInternetOnCommand.this, InternetJobService.class))
                    .setMinimumLatency(0)
                    .set
                    .build();
            sch.schedule(jobInfo);

             */

            /*
            var intent = new Intent(AccessInternetOnCommand.this, InternetJobService.class);
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

             */

            // AccessInternetOnCommand.this.startForegroundService(intent);

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
