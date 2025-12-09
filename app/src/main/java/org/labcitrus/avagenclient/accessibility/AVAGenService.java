package org.labcitrus.avagenclient.accessibility;

import static org.labcitrus.avagenclient.core.NodeFinder.findNodes;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.labcitrus.avagenclient.R;
import org.labcitrus.avagenclient.agent.ConversationAgent;
import org.labcitrus.avagenclient.agent.ServerCommunicator;
import org.labcitrus.avagenclient.agent.ToolCallManager;
import org.labcitrus.avagenclient.core.NodeQuery;
import org.labcitrus.avagenclient.core.ActionPerformer;
import org.labcitrus.avagenclient.core.Utils;
import org.labcitrus.avagenclient.stt.SpeechRecognizerManager;
import org.labcitrus.avagenclient.ui.FloatingPopupManager;

import com.google.gson.Gson;

import org.labcitrus.avagenclient.actionplan.ActionPlan;
import org.labcitrus.avagenclient.actionplan.ActionPlanExecutor;

import java.util.List;



/**
 * Test2VA Service is built on top of the accessibility service
 */
public class AVAGenService extends AccessibilityService {

    public static final String TAG = "AVAGenServiceSA";
    private static ActionPerformer actionPerformer;
    private static AVAGenService instance; // Static reference
    private SpeechRecognizerManager speechManager;
    private FloatingPopupManager popupManager;
    private ConversationAgent conversationAgent;
    private ToolCallManager toolCallManager;
    private ServerCommunicator serverCommunicator;

    // class fields (add)
    // Snapshot of the app we are serving (set when user taps the Test2VA button)
    private String currentAppId = "org.geeksforgeeks.simple_notes_application_java"; // TODO: set from SessionManager or foreground app
    // Raw foreground package (can include overlays like our own app or system UI)
    private String lastForegroundPackage = null;
    // Last non-overlay foreground package (i.e., real third-party app, excluding our client and system UI)
    private String lastNonOverlayPackage = null;
    // NEW:
    private ActionPlanExecutor actionPlanExecutor;
    private final Gson gson = new Gson();

    // private AppTask handler;
    FrameLayout t2vLayout;

    public static AVAGenService getInstance() {
        return instance; // Getter method for instance
    }

    @Override
    protected void onServiceConnected() {
        showGlobalActionBar();
        configureTest2vaImageView();

        // Initialize popup manager
        popupManager = new FloatingPopupManager(this);

        // Action perform service
        actionPerformer = new ActionPerformer(this);
        instance = this;

        // ActionPlan executor (JSON → plan → UI actions)
        actionPlanExecutor = new ActionPlanExecutor(this);

        // Instantiate the SpeechRecognizerManager
        speechManager = new SpeechRecognizerManager(this);

        // ------------------------------------------------------------------------
        // 1. START SESSION
        // ------------------------------------------------------------------------

        // Step 1: Initialize the tool manager and communicator
        toolCallManager = new ToolCallManager();
        serverCommunicator = new ServerCommunicator(this);  // 'this' is valid Context

        // Step 2: Initialize conversation agent
        conversationAgent = new ConversationAgent(serverCommunicator, toolCallManager);

        // Step 3: Start a new session
        conversationAgent.startSession(new ConversationAgent.Callback() {
            @Override
            public void onSuccess(String appMessage) {
                Log.i("Test2vaService", "[SESSION] Started: " + appMessage);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e("Test2vaService", "[SESSION] Failed: " + errorMessage);
            }
        });

        // ------------------------------------------------------------------------
        // 2. Session started, begin service and the UI window
        // ------------------------------------------------------------------------
        // Set delegate for mic icon callback
        popupManager.setSpeechListenerDelegate(new FloatingPopupManager.SpeechListenerDelegate() {
            @Override
            public void onMicTriggered() {

                speechManager.startListening(new SpeechRecognizerManager.SpeechResultListener() {
                    @Override
                    public void onResult(String result) {
                        Log.d(TAG, "Speech result: " + result);
                        popupManager.stopListening();
                        popupManager.addMessage(result, true); // user message

                        // Check if this is a confirmation after a pending tool call
                        if (toolCallManager.hasPendingToolCall() && isAffirmative(result)) {

                            // 1) Get app id from server
                            // Compare the app snapshot when popup opened vs. server-declared app_id
                            String serverAppId = conversationAgent.getServerSupportAppId();

                            // 2) Check app mismatch
                            if (serverAppId != null && !serverAppId.isEmpty() && currentAppId != null && !currentAppId.isEmpty()
                                    && !serverAppId.equals(currentAppId)) {
                                // App changed or mismatched – notify and DO NOT execute
                                popupManager.addMessage(
                                        " The requested task is for app: " + serverAppId +
                                                ", but you’re currently in: " + currentAppId +
                                                ". Switch back to the original app and say 'Yes' again.",
                                        false
                                );

                                Log.w(TAG, "App mismatch: serverAppId=" + serverAppId + " currentAppId=" + currentAppId);
                                // Keep the pending tool call so the user can confirm again after switching apps
                                return;
                            }

                            // 3) Get the action plan JSON returned by the server
                            //    TODO: implement this in ToolCallManager so it stores the full JSON string
                            String actionPlanJson = toolCallManager.getPendingActionPlanJson();
                            if (actionPlanJson == null || actionPlanJson.isEmpty()) {
                                Log.w(TAG, "[ACTION_PLAN] No JSON payload found in ToolCallManager");
                                popupManager.addMessage("No action plan found to execute.", false);
                                toolCallManager.clear();
                                return;
                            }

                            // Close the popup window
                            popupManager.removePopupWindow();


                            // Log action plan execution details
                            Log.d(TAG, "[ACTION_PLAN] Executing for appId=" + serverAppId);
                            Log.v(TAG, "[ACTION_PLAN] JSON: " + actionPlanJson);

                            // Small delay to let window hierarchy settle before node queries
                            Log.d(TAG, "execute here. ");

                            // 4) Parse JSON into a single ActionPlan
                            final String appIdForExec = serverAppId;
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    // actionPlanJson is expected to be a single ActionPlan, e.g.:
                                    // {
                                    //   "method_name": "accessStatistics",
                                    //   "steps": [ ... ]
                                    // }
                                    ActionPlan planToRun = gson.fromJson(actionPlanJson, ActionPlan.class);

                                    if (planToRun == null || planToRun.isEmpty()) {
                                        Log.w(TAG, "[ACTION_PLAN] Parsed plan is null/empty");
                                        popupManager.addMessage("Action plan is empty.", false);
                                        return;
                                    }

                                    Log.i(TAG, "[ACTION_PLAN] Running plan: " + planToRun.getMethodName());

                                    // Execute on background thread (avoid blocking main / accessibility)
                                    new Thread(() -> actionPlanExecutor.executePlan(appIdForExec, planToRun)).start();

                                } catch (Exception e) {
                                    Log.e(TAG, "[ACTION_PLAN] Failed to parse/execute", e);
                                    popupManager.addMessage("Failed to execute the action plan.", false);
                                }
                            }, 1000);

                            // Clear tool call to avoid duplicate executions
                            toolCallManager.clear();

                            // 5) Start a new session
                            conversationAgent.startSession(new ConversationAgent.Callback() {
                                @Override
                                public void onSuccess(String appMessage) {
                                    Log.i("Test2vaService", "[SESSION] Started: " + appMessage);
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    Log.e("Test2vaService", "[SESSION] Failed: " + errorMessage);
                                }
                            });

                        } else {
                            // No pending tool call, or user said something else — continue conversation
                            conversationAgent.fetchAppReply(result, popupManager, currentAppId);
                        }

                    }

                    @Override
                    public void onError(int error) {
                        Log.e(TAG, "Speech recognition error: " + error);
                        popupManager.stopListening();
                        popupManager.addMessage("Speech recognition failed", false);
                    }
                });
            }
        });

        Log.d(TAG,"onServiceConnected . . .");
    }



    /**
     *  In this example, when button click, the generated method will be invoked.
     */
    private void configureTest2vaImageView() {

        ImageView t2vButton = t2vLayout.findViewById(R.id.ava_gen_imageView);
        t2vButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "onClick: AVA-gen button");

                // Snapshot current foreground appId BEFORE opening popup
                currentAppId = resolveForegroundAppId();
                Log.d(TAG, "Snapshot appId before popup: " + currentAppId);

                if (popupManager != null) {
                    popupManager.showPopupWindow();

                    // Optional: greet the user only on first popup
                    popupManager.addMessage("Ready to serve app with id: " + currentAppId, false);
                    popupManager.addMessage("Hello! How can I help?", false); // from app
                }

            }
        });
    }


    /**
     * Wrapper method that retrieves nodes using `retrieveNodes()` and finds matching nodes based on provided queries.
     *
     * <p>Usage Example:</p>
     * <pre>{@code
     * List<AccessibilityNodeInfo> matchingNodes = findNode(
     *     withId("submit_button"),
     *     withText("Submit")
     * );
     * }</pre>
     *
     * @param queries The dynamic filtering conditions.
     * @return A list of matching AccessibilityNodeInfo nodes.
     */
    public static AccessibilityNodeInfo findNode(NodeQuery... queries) {
        Log.i(TAG,"findNode: queries numbers: " + queries.length);

        // Get instance of Test2vaService
        AVAGenService service = getInstance();
        if (service == null) {
            Log.e(TAG, "findNode: Service instance is null!");
            return null;
        }

        // Retrieve the all valid nodes to search
        List<AccessibilityNodeInfo> allNodes = Utils.retrieveAllChildrenFromNode(service.getRootNode());

        // Use NodeFinder.findNodes() to filter based on provided queries
        List<AccessibilityNodeInfo> foundNodes = findNodes(allNodes, queries);

        if (foundNodes.isEmpty()) {
            Log.i(TAG,"findNode: no node meet the searching criteria.");
            return null;
        }
        else if (foundNodes.size() > 1) {
            Log.i(TAG,"findNode: more than one nodes meet the searching criteria.");
            return foundNodes.get(0);
        } else {
            Log.i(TAG,"findNode: found one node meet the searching criteria.");
            return foundNodes.get(0);
        }
    }

    /**
     * Method to test if user say yes or similar
     * @param input
     * @return
     */
    private static boolean isAffirmative(String input) {
        String lowered = input.trim().toLowerCase();
        return lowered.equals("yes") || lowered.equals("sure") || lowered.equals("okay") || lowered.equals("ok");
    }

    /**
     *  This is an optional method where we will put an overlay button to allow use to trigger
     *  the Test2VA service.
     *  Developers can feel free to design its own way to trigger the Test2VA service in their
     *  applications
     */
    private void showGlobalActionBar() {
        // Create an overlay and display the action bar
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        t2vLayout = new FrameLayout(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.TOP;
        LayoutInflater inflater = LayoutInflater.from(this);
        inflater.inflate(R.layout.floating_test2va_button_layout, t2vLayout);
        wm.addView(t2vLayout, lp);

    }


    /**
     * Get the root node of current window when service is invoked.
     * @return root node
     */
    private AccessibilityNodeInfo getRootNode() {
        int tryCount = 0;
        while (tryCount < 3) {
            tryCount++; // update the number of try, max is three times

            // step1. wait for one second
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // step2. get the root node
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();

            // step3. return root node if not null
            if (rootNode != null) {
                rootNode.refresh();
                return rootNode;
            }
        }
        return null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                String pkgName = pkg.toString();
                // Always track the raw foreground package for debugging.
                lastForegroundPackage = pkgName;
                Log.d(TAG, "Foreground package: " + lastForegroundPackage);

                // Update lastNonOverlayPackage only when the foreground is a "real" app:
                // exclude our own client and system UI overlays.
                if (!getPackageName().equals(pkgName) && !"com.android.systemui".equals(pkgName)) {
                    lastNonOverlayPackage = pkgName;
                }
            }
        }
    }

    /**
     * Resolve the appId we should send to the server.
     *
     * - Prefer the last non-overlay foreground package (a real app).
     * - Fall back to inspecting the current root window, ignoring our own package and system UI.
     * - Ultimately fall back to the last snapshot (currentAppId) if nothing better is known.
     */
    private String resolveForegroundAppId() {
        // 1) Prefer the last observed non-overlay package (real app)
        if (lastNonOverlayPackage != null) {
            return lastNonOverlayPackage;
        }

        // 2) Fallback: inspect the current root window's package, but ignore overlays
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) {
            String pkg = root.getPackageName().toString();
            if (!getPackageName().equals(pkg) && !"com.android.systemui".equals(pkg)) {
                return pkg;
            }
        }

        // 3) Last resort: use the previously snapped appId
        return currentAppId;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt: something else happened");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null; // Clear instance when service stops
        if (popupManager != null) {
            popupManager.removePopupWindow();
        }
    }

    @Override
    public void onCreate() {

        super.onCreate();
    }


}
