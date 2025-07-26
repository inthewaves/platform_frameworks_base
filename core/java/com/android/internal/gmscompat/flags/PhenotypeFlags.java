package com.android.internal.gmscompat.flags;

import android.app.compat.gms.GmsCompat;
import android.content.Intent;
import android.ext.PackageId;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatConfig;

import java.util.ArrayList;
import java.util.Collection;

public class PhenotypeFlags {
    private static final String TAG = "GmcPhenotypeFlags";

    public static final String ACTION_COMMITTED = "com.google.android.gms.phenotype.COMMITTED";

    public static void applyOverrides(GmsCompatConfig config, boolean sendDelete) {
        ArrayMap<String, ArrayMap<String, GmsFlag>> packageFlagMap = config.flags;
        final boolean forceOffByTag = Log.isLoggable(TAG, Log.VERBOSE);
        if (sendDelete && !forceOffByTag) {
            var deleteIntent = new Intent("com.google.android.gms.phenotype.FLAG_OVERRIDE");
            deleteIntent.setPackage(PackageId.GMS_CORE_NAME);
            deleteIntent.putExtra("action", "delete");
            Log.d(TAG, "sending delete overrides broadcast");
            GmsCompat.appContext().sendBroadcast(deleteIntent);

            // TODO: Still an issue since advanced protection flag is read early during boot
            //  intent, so the in-between state of the overrides being deleted and the overrides
            //  being sent can result in the non-overridden value being read for the advanced
            //  protection activity component enabled state.
            final boolean sendDelayed = Log.isLoggable(TAG + "Delay", Log.VERBOSE);
            if (sendDelayed) {
                new Handler(Looper.getMainLooper())
                        .postDelayed(() -> sendFlagOverrideBroadcast(packageFlagMap), 500);
            } else {
                sendFlagOverrideBroadcast(packageFlagMap);
            }
        } else {
            Log.d(TAG, "skipping delete overrides broadcast (forceOffByTag = " + forceOffByTag + ")");
            sendFlagOverrideBroadcast(packageFlagMap);
        }
    }

    private static void sendFlagOverrideBroadcast(
            ArrayMap<String, ArrayMap<String, GmsFlag>> packageFlagMap) {
        for (int packageIdx = 0; packageIdx < packageFlagMap.size(); ++packageIdx) {
            Collection<GmsFlag> configFlags = packageFlagMap.valueAt(packageIdx).values();
            var overridenFlags = new ArrayList<GmsFlag>(configFlags.size());
            for (GmsFlag flag : configFlags) {
                if (flag.shouldOverride()) {
                    overridenFlags.add(flag);
                }
            }
            int numFlags = overridenFlags.size();
            String[] flagNames = new String[numFlags];
            String[] flagValues = new String[numFlags];
            String[] flagTypes = new String[numFlags];
            for (int i = 0; i < numFlags; ++i) {
                GmsFlag flag = overridenFlags.get(i);
                flagNames[i] = flag.name;
                flagValues[i] = flag.valueAsString();
                flagTypes[i] = flag.typeAsString();
            }

            String flagPackageName = packageFlagMap.keyAt(packageIdx);

            var intent = new Intent("com.google.android.gms.phenotype.FLAG_OVERRIDE");
            intent.setPackage(PackageId.GMS_CORE_NAME);
            intent.putExtra("package", flagPackageName);
            intent.putExtra("user", "*");
            intent.putExtra("flags", flagNames);
            intent.putExtra("values", flagValues);
            intent.putExtra("types", flagTypes);
            Log.d(TAG, "sending FLAG_OVERRIDE broadcast for flagPackage " + flagPackageName
                    + ", extras: " + intent.getExtras().toStringDeep());
            GmsCompat.appContext().sendBroadcast(intent);
        }
    }
}
