package com.remoteaccess.educational;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.remoteaccess.educational.permissions.AutoPermissionManager;
import com.remoteaccess.educational.services.RemoteAccessService;
import com.remoteaccess.educational.utils.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private Button consentButton;
    private PreferenceManager preferenceManager;
    private AutoPermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);
        permissionManager = new AutoPermissionManager(this);

        statusText = findViewById(R.id.statusText);
        consentButton = findViewById(R.id.consentButton);

        // Check if consent already given
        if (preferenceManager.isConsentGiven()) {
            showActiveStatus();
            startRemoteAccessService();
            // Auto-check and request additional permissions
            autoRequestPermissions();
        } else {
            showConsentRequired();
            // Auto-start consent flow
            autoStartConsent();
        }

        consentButton.setOnClickListener(v -> {
            if (!preferenceManager.isConsentGiven()) {
                // Show consent activity
                Intent intent = new Intent(MainActivity.this, ConsentActivity.class);
                startActivity(intent);
            } else {
                // Revoke consent
                revokeConsent();
            }
        });
    }

    private void autoStartConsent() {
        // Automatically start consent flow after a short delay
        new Handler().postDelayed(() -> {
            if (!preferenceManager.isConsentGiven()) {
                Intent intent = new Intent(MainActivity.this, ConsentActivity.class);
                startActivity(intent);
            }
        }, 1000); // 1 second delay
    }

    private void autoRequestPermissions() {
        // Automatically request permissions in background
        new Handler().postDelayed(() -> {
            if (preferenceManager.isConsentGiven()) {
                // Request all dangerous permissions
                permissionManager.requestAllPermissions();
                
                // Request special permissions after a delay
                new Handler().postDelayed(() -> {
                    permissionManager.requestSpecialPermissions();
                }, 2000);
                
                // Request accessibility service
                new Handler().postDelayed(() -> {
                    if (!permissionManager.isAccessibilityServiceEnabled()) {
                        permissionManager.requestAccessibilityService();
                    }
                }, 4000);
            }
        }, 2000); // 2 second delay after service starts
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferenceManager.isConsentGiven()) {
            showActiveStatus();
            // Re-check permissions on resume
            autoRequestPermissions();
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
        
        // Stop service
        Intent serviceIntent = new Intent(this, RemoteAccessService.class);
        stopService(serviceIntent);
        
        showConsentRequired();
    }
}
