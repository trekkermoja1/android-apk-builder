package com.remoteaccess.educational.utils;

public class Constants {
    
    // ========== SERVER CONFIGURATION ==========
    // DEVELOPMENT (Local Testing)
    // Use your computer's IP address (not localhost!)
    public static final String SERVER_URL_DEV = "http://192.168.1.100:5000";
    
    // PRODUCTION (Deployed Server)
    // Use your domain or server IP
    public static final String SERVER_URL_PROD = "https://yourdomain.com";
    
    // CURRENT SERVER (Change this for dev/prod)
    public static final String SERVER_URL = SERVER_URL_DEV;

    // ========== SOCKET.IO CONFIGURATION ==========
    public static final String SOCKET_PATH = "/socket.io";
    public static final int SOCKET_TIMEOUT = 10000;
    public static final int HEARTBEAT_INTERVAL = 30000;

    // ========== API ENDPOINTS ==========
    public static final String API_BASE = SERVER_URL + "/api";
    public static final String API_AUTH = API_BASE + "/auth";
    public static final String API_DEVICES = API_BASE + "/devices";

    // ========== APP CONFIGURATION ==========
    public static final String APP_VERSION = "1.0.0";
    public static final boolean ENABLE_LOGGING = true;

    // ========== PERMISSIONS ==========
    public static final String[] REQUIRED_PERMISSIONS = {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.FOREGROUND_SERVICE"
    };

    public static final String[] OPTIONAL_PERMISSIONS = {
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    };
}
