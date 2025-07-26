package com.android.internal.gmscompat.flags;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.ext.PackageId;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatApp;
import com.android.internal.gmscompat.GmsCompatConfig;
import com.android.internal.gmscompat.GmsHooks;

import java.util.Arrays;

public class GmsFlagOverrides {
    private static final String TAG = "GmsFlagOverrides";

    private static long lastSeenVersion;

    public static void init(Context ctx) {
        try {
            lastSeenVersion = GmsCompatApp.iGms2Gca().getLastSeenConfigVersion();
        } catch (RemoteException e) {
            throw GmsCompatApp.callFailed(e);
        }

        Arrays.asListHook = (Object[] arr) -> {
            if (arr.length == 2 && "com.google.android.apps.internal.mobdog".equals(arr[0]) && "com.google.android.apps.mobileutilities".equals(arr[1])) {
                String[] replacement = new String[] { (String) arr[0], (String) arr[1], PackageId.GMS_CORE_NAME, };
                Log.d(TAG, "Arrays.asListHook: replaced " + Arrays.toString(arr) + " with " + Arrays.toString(replacement));
                return replacement;
            }
            return arr;
        };

        var receiver = new BroadcastReceiver() {
            private boolean receivedPhenotypeCommittedBroadcast;

            @Override
            public void onReceive(Context context, Intent intent) {
                if (PhenotypeFlags.ACTION_COMMITTED.equals(intent.getAction())) {
                    if (receivedPhenotypeCommittedBroadcast) {
                        return;
                    }
                    receivedPhenotypeCommittedBroadcast = true;
                }
                Log.d(TAG, "received " + intent);
                applyOverrides(true);
            }
        };
        // Most phenotype flags and all Gservices flags are stored on user-encrypted storage,
        // i.e. they can't be updated while the device is in Direct Boot state
        var filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
        // In some cases phenotype service isn't ready to accept overrides at user_unlocked time and
        // at process init time. It's always ready by the time phenotype ACTION_COMMITTED is sent.
        filter.addAction(PhenotypeFlags.ACTION_COMMITTED);
        ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);

        // Avoid sending delete broadcast on init, as another delete will be sent by
        // a broadcast later. Appears to be some race conditions in GMS
        applyOverrides(false);
    }

    public static void applyOverrides(boolean shouldDelete) {
        GmsCompatConfig config = GmsHooks.config();
        GservicesFlags.applyOverrides(config);
        Log.d(TAG, "lastAppliedVersion=" + lastSeenVersion + ", config.version=" + config.version);
        final boolean forceOnByTag = Log.isLoggable(TAG+"Delete", Log.VERBOSE);
        final boolean sendDelete = forceOnByTag || (shouldDelete && lastSeenVersion != config.version);
        if (sendDelete) {
            lastSeenVersion = config.version;
            try {
                GmsCompatApp.iGms2Gca().setLastSeenConfigVersion(config.version);
            } catch (RemoteException e) {
                throw GmsCompatApp.callFailed(e);
            }
            PhenotypeFlags.applyOverrides(config, true);
        } else {
            PhenotypeFlags.applyOverrides(config, false);
        }

    }
}
