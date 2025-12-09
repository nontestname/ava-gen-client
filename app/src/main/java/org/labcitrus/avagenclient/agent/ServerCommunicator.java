package org.labcitrus.avagenclient.agent;

import android.util.Log;
import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ServerCommunicator handles the HTTP handshake and communication
 * between the Android client and the Test2VA backend server.
 *
 * Based on protocol defined in `Client-Server Handshake: Test2VA Agent API`.
 *
 * Endpoints:
 *   - POST /agent/start_session -> returns new session_id (empty POST body, no app_id required)
 *   - POST /agent/request       -> sends user chat message
 *
 * TODO[AWS]:
 *  - Inject server base URL from configuration if needed
 *  - Support HTTPS
 *  - Add retry logic with exponential backoff
 */
public class ServerCommunicator {

    private static final String TAG = "ServerCommunicator";

    // Media type for JSON requests
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Shared HTTP client and background thread pool
    private static final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    // TODO[AWS]: Replace with environment-configured base URL
    // private static final String BASE_URL = "http://127.0.0.1:8000";
    private static final String BASE_URL = "http://10.0.2.2:8000"; // for emulator

    // === Endpoints ===
    private static final String START_SESSION_PATH = "/agent/start_session";
    private static final String AGENT_REQUEST_PATH = "/agent/request";

    public ServerCommunicator(Context context) {
        // No TokenManager initialization since auth is removed
    }

    // ------------------------------------------------------------
    // 1️⃣ SessionCallback Interface
    // ------------------------------------------------------------
    public interface SessionCallback {
        void onSuccess(String sessionId);
        void onFailure(String errorMessage);
    }

    // ------------------------------------------------------------
    // 2️⃣ AgentCallback Interface
    // ------------------------------------------------------------
    public interface AgentCallback {
        void onSuccess(String rawJsonResponse);
        void onFailure(String errorMessage);

    }

    // ------------------------------------------------------------------------
    // 1. START SESSION
    // ------------------------------------------------------------------------
    /**
     * Initiates a new session with the Test2VA server.
     * Calls POST /agent/start_session with an empty body. Server returns a new session_id.
     */
    public void startSession(SessionCallback callback) {
        executor.execute(() -> {
            try {
                String url = BASE_URL + START_SESSION_PATH;
                // Empty POST body for starting a session
                RequestBody body = RequestBody.create(new byte[0], null);

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        String sessionId = json.get("session_id").getAsString();
                        callback.onSuccess(sessionId);
                    } else {
                        callback.onFailure("Failed to start session. HTTP " + response.code());
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Network error in startSession: " + e.getMessage());
                callback.onFailure("Network error: " + e.getMessage());
            } catch (Exception e) {
                callback.onFailure("Invalid session response: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------
    // 2. SEND CLIENT REQUEST
    // ------------------------------------------------------------------------
    /**
     * Sends user chat message to the backend agent via POST /agent/request.
     *
     * Expected request body schema:
     * {
     *   "session_id": "...",
     *   "app_id": "...",   // provided by client based on foreground app
     *   "message": "user request"
     * }
     */

    /**
     * Sends a user chat message to the backend agent via POST /agent/request.
     *
     * This method performs only:
     *   - Network transport (HTTP POST)
     *   - HTTP POST transport only (no authentication)
     *   - Raw JSON handling
     */
    public void sendAgentRequest(
            String sessionId,
            String appId,
            String currentUserChat,
            AgentCallback callback
    ) {
        executor.execute(() -> {
            try {
                // --- 1️⃣ Build JSON request body ---
                JsonObject bodyJson = new JsonObject();
                bodyJson.addProperty("session_id", sessionId);
                bodyJson.addProperty("app_id", appId);
                bodyJson.addProperty("message", currentUserChat);

                String jsonBody = gson.toJson(bodyJson);

                String url = BASE_URL + AGENT_REQUEST_PATH;

                RequestBody body = RequestBody.create(jsonBody, JSON);

                Log.d(TAG, "[AgentRequest] " + jsonBody);

                // --- 2️⃣ Prepare HTTP POST ---
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build();

                // --- 3️⃣ Execute network call ---
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        String error = "Agent request failed: HTTP " + response.code();
                        Log.w(TAG, error);
                        callback.onFailure(error);
                        return;
                    }

                    // --- 4️⃣ Return raw JSON to ConversationAgent ---
                    String responseBody = response.body().string();
                    Log.d(TAG, "[AgentResponse] " + responseBody);
                    callback.onSuccess(responseBody);

                }

            } catch (IOException e) {
                String msg = "Network error in sendAgentRequest: " + e.getMessage();
                Log.e(TAG, msg);
                callback.onFailure(msg);
            } catch (Exception e) {
                String msg = "Unexpected error in sendAgentRequest: " + e.getMessage();
                Log.e(TAG, msg);
                callback.onFailure(msg);
            }
        });
    }

    // ------------------------------------------------------------------------
    // 3. (Optional) Utility — Future Extension
    // ------------------------------------------------------------------------
    /**
     * Utility for dynamically switching server endpoints (local vs AWS).
     * Future extension: allow AppConfig or runtime switching.
     */
    public static String getBaseUrl() {
        return BASE_URL;
    }
}