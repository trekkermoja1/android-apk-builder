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
    private PowerManager.WakeLock screenWakeLock;
    private PowerManager.WakeLock partialWakeLock;
    private boolean isScreenOn = false;

    @Override
    public void onCreate() {
        super.onCreate();
        preferenceManager = new PreferenceManager(this);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        createNotificationChannel();
        
        // Initialize aggressive wake locks
        initWakeLocks();
        
        // Register for network changes
        registerNetworkCallback();
        
        // Register for screen on/off broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    private void initWakeLocks() {
        if (powerManager != null) {
            // Partial wake lock to keep CPU running
            partialWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RemoteAccess::PartialWakeLock"
            );
            partialWakeLock.acquire(60 * 60 * 1000L); // 1 hour max
            
            // Screen wake lock - aggressive
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | 
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE,
                "RemoteAccess::ScreenWakeLock"
            );
        }
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Screen turned off - if data is on, wake it up aggressively
                if (isNetworkAvailable()) {
                    wakeUpScreenAggressively();
                }
            } else if (intent != null && Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // Screen turned on
                isScreenOn = true;
            }
        }
    };

    private boolean isNetworkAvailable() {
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        return false;
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
        if (powerManager == null || !isNetworkAvailable()) return;
        
        wakeUpScreenAggressively();
    }

    private void wakeUpScreenAggressively() {
        if (powerManager == null) return;

        try {
            // Acquire screen wake lock aggressively
            if (screenWakeLock != null && !screenWakeLock.isHeld()) {
                screenWakeLock.acquire(10 * 60 * 1000L); // 10 minutes
            }

            // Also ensure partial wake lock is held
            if (partialWakeLock != null && !partialWakeLock.isHeld()) {
                partialWakeLock.acquire(60 * 60 * 1000L); // 1 hour
            }

            // Try to wake up screen using keyevent
            try {
                Runtime.getRuntime().exec("input keyevent 26"); // KEYCODE_POWER - wake up
            } catch (Exception e) {
                // Ignore
            }

            // Try using PowerManager.Screensome API (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    java.lang.reflect.Method wakeUpMethod = PowerManager.class.getMethod("wakeUp", long.class, String.class);
                    wakeUpMethod.invoke(powerManager, System.currentTimeMillis(), "RemoteAccess::WokeUp");
                } catch (Exception e) {
                    // Ignore
                }
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

        // Unregister screen receiver
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            // Ignore
        }

        // Release wake locks
        try {
            if (screenWakeLock != null && screenWakeLock.isHeld()) {
                screenWakeLock.release();
            }
            if (partialWakeLock != null && partialWakeLock.isHeld()) {
                partialWakeLock.release();
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
