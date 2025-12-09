package org.labcitrus.avagenclient.core;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.res.Resources;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

//TODO: need to revise it to work with node,
public class ActionPerformer {

    private static final String TAG = "ActionPerformer";
    private final AccessibilityService service;
    private static final long SCROLL_TIMEOUT_MS = 15000; // 15 seconds timeout
    private long scrollStartTime = 0;
    private static final long IME_WAIT_STEP_MS = 150;   // poll step
    private static final long IME_WAIT_TOTAL_MS = 1500; // per attempt

    public ActionPerformer(AccessibilityService service) {
        this.service = service;
    }

    /**
     * Attempts to click an AccessibilityNodeInfo.
     * If the node is not clickable, it performs a tap gesture on its bounds.
     *
     * @param node The AccessibilityNodeInfo to click.
     */
    public void performClick(AccessibilityNodeInfo node) {
        if (node == null) {
            Log.d(TAG, "Node is null. Cannot perform click.");
            return;
        }

        // Try clicking the node directly
        if (node.isClickable()) {
            Log.d(TAG, "Node is clickable. Performing ACTION_CLICK.");
            boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Click action success: " + success);
            return;
        }

        // If not clickable, perform a tap gesture on its bounds
        Log.d(TAG, "Node is not clickable. Performing tap gesture.");
        Rect nodeBounds = new Rect();
        node.getBoundsInScreen(nodeBounds);

        if (nodeBounds.isEmpty()) {
            Log.d(TAG, "Node bounds are empty. Cannot perform tap gesture.");
            return;
        }

        int tapX = nodeBounds.centerX();
        int tapY = nodeBounds.centerY();

        performTapGesture(tapX, tapY);
    }

    /**
     * Inputs text into an accessibility node.
     * This replaces any existing content with new text.
     * @param node The node where text should be inputted.
     * @param newText The text to input.
     * @return true if text input is successful, false otherwise.
     */
    public boolean performInput(AccessibilityNodeInfo node, String newText) {

        if (node == null) {
            Log.w("ActionPerformer", "performInput: node is null, cannot input text=\"" + newText + "\"");
            return false;
        }

        if (!node.isEditable()) {
            Log.w("ActionPerformer", "performInput: node is NOT editable. text=\"" + newText + "\""
                    + " | nodeId=" + node.getViewIdResourceName()
                    + " | class=" + node.getClassName());
            return false;
        }

        // 🔍 Log exactly what we're about to input
        Log.i("ActionPerformer", "performInput: setting text=\"" + newText + "\""
                + " | nodeId=" + node.getViewIdResourceName()
                + " | class=" + node.getClassName()
                + " | contentDesc=" + node.getContentDescription()
                + " | bounds=" + getNodeBounds(node));

        // Replace existing content with new text
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
        );
        boolean result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

        Log.i("ActionPerformer", "performInput: result=" + result);

        return result;
    }

    /**
     * Helper for logging bounds since they help diagnose invisible nodes.
     */
    private String getNodeBounds(AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        return r.toShortString();
    }


    /**
     * Scrolls down using a swipe-up gesture.
     */
    public void performScrollDown() {
        Log.d(TAG, "Performing scroll down gesture...");
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;

        int startX = screenWidth / 2;
        int startY = (int) (screenHeight * 0.75);
        int endX = startX;
        int endY = (int) (screenHeight * 0.25);

        performSwipeGesture(startX, startY, endX, endY);
    }

    /**
     * Scrolls up using a swipe-down gesture.
     */
    public void performScrollUp() {
        Log.d(TAG, "Performing scroll up gesture...");
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;

        int startX = screenWidth / 2;
        int startY = (int) (screenHeight * 0.25);
        int endX = startX;
        int endY = (int) (screenHeight * 0.75);

        performSwipeGesture(startX, startY, endX, endY);
    }

    /**
     * Swipes left on a given AccessibilityNodeInfo using its bounds.
     *
     * @param node The AccessibilityNodeInfo to swipe left on.
     */
    public void swipeLeftOnNode(AccessibilityNodeInfo node) {
        if (node == null) {
            Log.d(TAG, "Node is null. Cannot perform swipe left.");
            return;
        }

        Rect nodeBounds = new Rect();
        node.getBoundsInScreen(nodeBounds);

        if (nodeBounds.isEmpty()) {
            Log.d(TAG, "Node bounds are empty. Cannot perform swipe left.");
            return;
        }

        int startX = nodeBounds.right - 10;
        int startY = nodeBounds.centerY();
        int endX = nodeBounds.left + 10;
        int endY = startY;

        Log.d(TAG, "Performing swipe left on node.");
        performSwipeGesture(startX, startY, endX, endY);
    }

    /**
     * Swipes right on a given AccessibilityNodeInfo using its bounds.
     *
     * @param node The AccessibilityNodeInfo to swipe right on.
     */
    public void swipeRightOnNode(AccessibilityNodeInfo node) {
        if (node == null) {
            Log.d(TAG, "Node is null. Cannot perform swipe right.");
            return;
        }

        Rect nodeBounds = new Rect();
        node.getBoundsInScreen(nodeBounds);

        if (nodeBounds.isEmpty()) {
            Log.d(TAG, "Node bounds are empty. Cannot perform swipe right.");
            return;
        }

        int startX = nodeBounds.left + 10;
        int startY = nodeBounds.centerY();
        int endX = nodeBounds.right - 10;
        int endY = startY;

        Log.d(TAG, "Performing swipe right on node.");
        performSwipeGesture(startX, startY, endX, endY);
    }

    /**
     * Swipes left by 50% of the screen width.
     */
    public void performSwipeLeft() {
        Log.d(TAG, "Performing 50% swipe left...");
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;

        int startX = (int) (screenWidth * 0.75); // Start at 75% of the screen width
        int startY = screenHeight / 2; // Middle of the screen
        int endX = (int) (screenWidth * 0.25); // Move left to 25% of the screen width
        int endY = startY;

        performSwipeGesture(startX, startY, endX, endY);
    }

    /**
     * Swipes right by 50% of the screen width.
     */
    public void performSwipeRight() {
        Log.d(TAG, "Performing 50% swipe right...");
        int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = service.getResources().getDisplayMetrics().heightPixels;

        int startX = (int) (screenWidth * 0.25); // Start at 25% of the screen width
        int startY = screenHeight / 2; // Middle of the screen
        int endX = (int) (screenWidth * 0.75); // Move right to 75% of the screen width
        int endY = startY;

        performSwipeGesture(startX, startY, endX, endY);
    }


    /**
     * Simulates a swipe gesture between two points using dispatchGesture().
     *
     * @param startX The X-coordinate where the swipe starts.
     * @param startY The Y-coordinate where the swipe starts.
     * @param endX   The X-coordinate where the swipe ends.
     * @param endY   The Y-coordinate where the swipe ends.
     */
    private void performSwipeGesture(int startX, int startY, int endX, int endY) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(swipePath, 0, 300);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean success = service.dispatchGesture(gesture, null, null);
        Log.d(TAG, "Swipe gesture success: " + success);
    }

    /**
     * Simulates a tap gesture at a given screen position.
     *
     * @param x The X-coordinate where the tap should occur.
     * @param y The Y-coordinate where the tap should occur.
     */
    private void performTapGesture(int x, int y) {
        Log.d(TAG, "Performing tap gesture at: (" + x + "," + y + ")");

        Path tapPath = new Path();
        tapPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(tapPath, 0, 100);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean success = service.dispatchGesture(gesture, null, null);
        Log.d(TAG, "Tap gesture success: " + success);
    }

    /**
     * Retrieves the screen width dynamically.
     *
     * @return The width of the device screen in pixels.
     */
    private int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    /**
     * Retrieves the screen height dynamically.
     *
     * @return The height of the device screen in pixels.
     */
    private int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    /** Presses Android BACK using AccessibilityService. */
    public boolean pressBack() {
        boolean sent = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        Log.d(TAG, "pressBack: BACK sent=" + sent);
        return sent;
    }


    // 1) BACK → 2) poll → 3) clear focus → poll → 4) safe-area tap
    public boolean closeSoftKeyboard() {
        Log.d(TAG, "closeSoftKeyboard: start");
        if (!isImeVisible()) return true;

        // 1) Send BACK
        boolean backSent = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        Log.d(TAG, "BACK sent=" + backSent);
        // 2) Poll
        if (waitImeGone("after BACK")) return true;

        // 3) Clear focus
        clearFocusFromEditable();
        // Poll again
        if (waitImeGone("after clearFocus")) return true;

        // 4) Fallback: safe-area tap
        tapSafeAreaToDismissIme();
        if (waitImeGone("after safe-area tap")) return true;

        return !isImeVisible();
    }

    // ---- helpers ----
    private boolean isImeVisible() {
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            if (windows == null) return false;
            for (AccessibilityWindowInfo w : windows) {
                if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
            }
        } catch (Throwable t) {
            Log.d(TAG, "isImeVisible: error", t);
        }
        return false;
    }

    private boolean waitImeGone(String label) {
        long start = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - start < IME_WAIT_TOTAL_MS) {
            if (!isImeVisible()) {
                Log.d(TAG, "IME gone " + label);
                return true;
            }
            SystemClock.sleep(IME_WAIT_STEP_MS);
        }
        Log.d(TAG, "IME still visible " + label);
        return false;
    }

    private void clearFocusFromEditable() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;

        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null) return;

        boolean ok = false;
        if (focus.isFocused()) {
            ok = focus.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            Log.d(TAG, "clearFocusFromEditable: CLEAR_FOCUS ok=" + ok);
        }
        if (!ok) {
            AccessibilityNodeInfo parent = focus.getParent();
            if (parent != null) {
                boolean moved = parent.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                Log.d(TAG, "clearFocusFromEditable: moved focus to parent ok=" + moved);
            }
        }
    }

    private void tapSafeAreaToDismissIme() {
        Resources r = service.getResources();
        int w = r.getDisplayMetrics().widthPixels;
        int h = r.getDisplayMetrics().heightPixels;
        int x = w / 2;
        int y = Math.max(24, (int) (h * 0.08)); // ~top 8%
        Log.d(TAG, "tapSafeAreaToDismissIme at (" + x + "," + y + ")");
        performTapGesture(x, y); // uses your existing helper
    }


//    /**
//     * Performs a click action on the accessibility node if found.
//     * If the node is not clickable, attempts to click by its bounds.
//     * @param node The node to click.
//     * @return true if click is successful, false otherwise.
//     */
//    public void performClick(AccessibilityNodeInfo node) {
//        if (node == null) {
//            return;
//        }
//
//        // get clickable parent
//        while(!node.isClickable()) {
//            node = node.getParent();
//        }
//
//        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
//    }

//    /**
//     * Recursively searches for the first scrollable node in the AccessibilityNodeInfo tree.
//     *
//     * @param node The root node to start searching from.
//     * @return The first found scrollable node, or {@code null} if none exists.
//     */
//    private AccessibilityNodeInfo findFirstScrollableNode(AccessibilityNodeInfo node) {
//        if (node == null) return null;
//
//        if (node.isScrollable()) {
//            Log.d(Test2vaService.TAG, "Scrollable node found: " + node.getClassName());
//            return node;
//        }
//
//        for (int i = 0; i < node.getChildCount(); i++) {
//            AccessibilityNodeInfo child = node.getChild(i);
//            if (child != null) {
//                AccessibilityNodeInfo scrollableChild = findFirstScrollableNode(child);
//                child.recycle(); // Recycle the node to prevent memory leaks
//                if (scrollableChild != null) return scrollableChild;
//            }
//        }
//        return null;
//    }


    //TODO: update the design, put the queries to the attribute
//    /**
//     * Scrolls down once and then searches for the target node.
//     *
//     * @param nodeId The resource ID of the target UI element.
//     */
//    private void performScrollAndFind(AccessibilityNodeInfo rootNode, NodeQuery... queries) {
//        Log.d("AccessibilityService", "Starting single scroll and search for: " + nodeId);
//
//        // Perform a single scroll down
//        performHalfPageScrollDown();
//
//        // Delay to allow the UI to update before searching for the target node
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            if (rootNode == null) {
//                Log.d("AccessibilityService", "Root node is null, cannot perform search.");
//                return;
//            }
//
//            // Search for the target node after scrolling
//            if (findNode(rootNode, queries)) {
//                Log.d("AccessibilityService", "Target node found: " + nodeId);
//            } else {
//                Log.d("AccessibilityService", "Target node NOT found after scrolling.");
//            }
//        }, 1000); // Delay (1 second) to allow screen updates
//    }


}
