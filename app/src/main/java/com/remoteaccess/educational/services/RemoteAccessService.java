package com.remoteaccess.educational.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.remoteaccess.educational.MainActivity;
import com.remoteaccess.educational.network.SocketManager;
import com.remoteaccess.educational.utils.DeviceInfo;
import com.remoteaccess.educational.utils.PreferenceManager;

public class RemoteAccessService extends Service {

    private static final String CHANNEL_ID = "RemoteAccessChannel";
    private static final int NOTIFICATION_ID = 1;

    private SocketManager socketManager;
    private PreferenceManager preferenceManager;
    private ConnectivityManager connectivityManager;
    private PowerManager powerManager;
    private NetworkCallback networkCallback;
    private boolean isScreenOn = false;

    @Override
    public void onCreate() {
        super.onCreate();
        preferenceManager = new PreferenceManager(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        createNotificationChannel();
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        if (connectivityManager != null) {
            networkCallback = new NetworkCallback();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            }
        }
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            // Network is available - wake up screen
            wakeUpScreen();
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            // Network lost - release wake lock
            releaseWakeLock();
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            boolean hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if (hasInternet) {
                wakeUpScreen();
            } else {
                releaseWakeLock();
            }
        }
    }

    private void wakeUpScreen() {
        if (powerManager == null) return;

        try {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "RemoteAccess::ScreenWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes max

            // Also try to turn on screen via window flags if activity context available
            try {
                android.app.Activity activity = (android.app.Activity) getApplicationContext();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        }
                    });
                }
            } catch (Exception e) {
                // Ignore if not in activity context
            }

            isScreenOn = true;
        } catch (Exception e) {
            // Ignore
        }
    }

    private void releaseWakeLock() {
        isScreenOn = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification());

        // Initialize socket connection
        if (preferenceManager.isConsentGiven()) {
            connectToServer();
        }

        return START_STICKY;
    }

    private void connectToServer() {
        String serverUrl = preferenceManager.getServerUrl();
        String deviceId = DeviceInfo.getDeviceId(this);

        socketManager = new SocketManager(this, serverUrl);
        socketManager.connect();
        socketManager.registerDevice(deviceId);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Remote Access Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps remote access connection active");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Access Active")
            .setContentText("Your device is connected and can be managed remotely")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (socketManager != null) {
            socketManager.disconnect();
        }

        // Unregister network callback
        try {
            if (networkCallback != null && connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Anti-Kill: Restart service if killed
        try {
            Intent restartIntent = new Intent(this, RemoteAccessService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
