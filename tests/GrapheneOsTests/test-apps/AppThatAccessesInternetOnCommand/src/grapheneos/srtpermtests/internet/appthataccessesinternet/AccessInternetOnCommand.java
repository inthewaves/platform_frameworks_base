package grapheneos.srtpermtests.internet.appthataccessesinternet;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import java.net.InetAddress;

public class AccessInternetOnCommand extends Service {
    private IAccessInternetOnCommand.Stub mBinder = new IAccessInternetOnCommand.Stub() {
        public void accessInternet() {
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
