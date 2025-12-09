package org.labcitrus.avagenclient.agent;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import org.labcitrus.avagenclient.ui.FloatingPopupManager;


import java.util.ArrayList;
import java.util.List;

/**
 * ConversationAgent coordinates the end-to-end interaction with the backend server.
 * It handles session start, user message dispatch, and agent reply processing.
 *
 */
public class ConversationAgent {

    private final ServerCommunicator serverCommunicator;
    private final ToolCallManager toolCallManager;

    /** Session ID returned by the backend after session handshake */
    private String sessionId = null;

    /** Latest appId declared by the server in its response (used for app mismatch checks) */
    private String serverSupportAppId = null;

    /** Callback interface to deliver server replies to UI or calling logic */
    public interface Callback {
        void onSuccess(String appMessage);
        void onFailure(String errorMessage);
    }

    public ConversationAgent(ServerCommunicator serverCommunicator, ToolCallManager toolCallManager) {
        this.serverCommunicator = serverCommunicator;
        this.toolCallManager = toolCallManager;
    }

    /**
     * Starts a new backend session. The client sends an empty POST body to /agent/start_session and receives a session_id. Any existing session_id is overwritten.
     *
     */
    public void startSession(Callback callback) {
        serverCommunicator.startSession(new ServerCommunicator.SessionCallback() {
            @Override
            public void onSuccess(String newSessionId) {
                sessionId = newSessionId;
                Log.i("ConversationAgent", "[SESSION] New session started: " + newSessionId);
                callback.onSuccess("Session started successfully.");
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e("ConversationAgent", "[SESSION] Failed to start: " + errorMessage);
                callback.onFailure("Unable to start session: " + errorMessage);
            }
        });
    }

    /**
     * Sends a user message to the backend agent using the current session ID.
     * Must be called after session has been initialized.
     */
    public void sendUserMessage(String appId, String userMessage, Callback callback) {
        if (sessionId == null) {
            callback.onFailure("No active session");
            return;
        }

        serverCommunicator.sendAgentRequest(sessionId, appId, userMessage, new ServerCommunicator.AgentCallback() {
            @Override
            public void onSuccess(String rawJson) {
                // Now parse rawJson → detect content vs tool_call
                parseAgentResponse(rawJson, callback);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure("Agent error: " + errorMessage);
            }
        });
    }

    // Parses backend JSON responses. Handles normal content replies, clarification messages, and
    // top-level action_plan responses that contain action-plan execution requests.
    private void parseAgentResponse(String rawJson, Callback callback) {
        try {
            JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject();

            // Save latest app_id from response, if present
            if (json.has("app_id") && !json.get("app_id").isJsonNull()) {
                serverSupportAppId = json.get("app_id").getAsString();
            }

            // Update session if server provides the next_session_id at top level
            updateSessionFromNextId(json);

            // Normalize the response type (if any)
            String type = null;
            if (json.has("type") && !json.get("type").isJsonNull()) {
                type = json.get("type").getAsString();
            }

            // 1) Handle top-level action_plan responses from the server
            if ("action_plan".equals(type)) {
                // Extract the inner action_plan object if present
                if (json.has("action_plan") && json.get("action_plan").isJsonObject()) {
                    JsonObject planObj = json.getAsJsonObject("action_plan");
                    // Store only the ActionPlan JSON for execution
                    toolCallManager.setPendingActionPlan(planObj.toString());
                } else {
                    // Fallback: store full JSON if no nested action_plan object
                    toolCallManager.setPendingActionPlan(json.toString());
                }

                // Prefer server-provided message for confirmation
                String serverMessage = null;
                if (json.has("message") && !json.get("message").isJsonNull()) {
                    serverMessage = json.get("message").getAsString();
                }

                String msg;
                if (serverMessage != null && !serverMessage.isEmpty()) {
                    msg = serverMessage + "\nReply 'Yes' to continue.";
                } else {
                    msg = "I’m about to execute an action plan.\nReply 'Yes' to continue.";
                }

                callback.onSuccess(msg);
                return;
            }

            // 2) Handle clarification responses (e.g., intent mismatch)
            if ("clarification".equals(type)) {
                String clarificationMsg = null;
                if (json.has("message") && !json.get("message").isJsonNull()) {
                    clarificationMsg = json.get("message").getAsString();
                }

                if (clarificationMsg == null || clarificationMsg.isEmpty()) {
                    clarificationMsg = "The agent could not match your request to any supported intents.";
                }

                callback.onSuccess(clarificationMsg);
                return;
            }

            // 3) Generic content-style response from server
            if (json.has("content") && !json.get("content").isJsonNull()) {
                String content = json.get("content").getAsString();
                callback.onSuccess(content);
                return;
            }

            // 4) Generic message-style response (for any other type)
            if (json.has("message") && !json.get("message").isJsonNull()) {
                String message = json.get("message").getAsString();
                callback.onSuccess(message);
                return;
            }

            // Unknown or invalid response structure
            callback.onFailure("Invalid agent response");

        } catch (Exception e) {
            Log.e("ConversationAgent", "Error parsing agent response: " + e.getMessage());
            callback.onFailure("Error parsing server response.");
        }
    }

    /**
     * Returns the current server-supported app ID, if available.
     */
    public String getServerSupportAppId() {
        return serverSupportAppId;
    }

    /**
     * Resets session info (e.g., on logout or restart).
     */
    public void clearSession() {
        sessionId = null;
    }

    /**
     * Public API used by the floating popup UI.
     * Sends the message and updates the popup with the server's reply.
     *
     * @param userMessage User's spoken text
     * @param popupManager Popup UI manager
     * @param appId Foreground app ID when popup was opened
     */
    public void fetchAppReply(String userMessage, FloatingPopupManager popupManager, String appId) {
        sendUserMessage(appId, userMessage, new Callback() {
            @Override
            public void onSuccess(String appMessage) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    popupManager.addMessage(appMessage, false);
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    popupManager.addMessage("Server error: " + errorMessage, false);
                });
            }
        });
    }

    /**
     * If the given JSON object contains a non-empty "next_session_id", switch the client session to it.
     */
    private void updateSessionFromNextId(JsonObject obj) {
        if (obj == null) return;
        if (obj.has("next_session_id") && !obj.get("next_session_id").isJsonNull()) {
            String nextId = obj.get("next_session_id").getAsString();
            if (nextId != null && !nextId.isEmpty()) {
                sessionId = nextId;
                Log.i("ConversationAgent", "[SESSION] Switched to next_session_id: " + nextId);
            }
        }
    }
}
