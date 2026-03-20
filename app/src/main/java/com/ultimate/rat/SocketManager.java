package com.ultimate.rat;

import android.content.Context;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;
import java.net.URISyntaxException;

public class SocketManager {
    
    private static SocketManager instance;
    private Socket socket;
    private Context context;
    private String serverUrl = "http://localhost:5000";
    
    private SocketManager(Context context) {
        this.context = context;
    }
    
    public static synchronized SocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new SocketManager(context);
        }
        return instance;
    }
    
    public void connect() {
        try {
            socket = IO.socket(serverUrl);
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    
    public void emit(String event, JSONObject data) {
        if (socket != null && socket.connected()) {
            socket.emit(event, data);
        }
    }
    
    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
        }
    }
    
    public boolean isConnected() {
        return socket != null && socket.connected();
    }
}
