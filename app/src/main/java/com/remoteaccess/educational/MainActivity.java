package com.remoteaccess.educational;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.remoteaccess.educational.services.RemoteAccessService;
import com.remoteaccess.educational.utils.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private Button consentButton;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);

        statusText = findViewById(R.id.statusText);
        consentButton = findViewById(R.id.consentButton);

        // Check if consent already given
        if (preferenceManager.isConsentGiven()) {
            showActiveStatus();
            startRemoteAccessService();
        } else {
            showConsentRequired();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (preferenceManager.isConsentGiven()) {
            showActiveStatus();
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
