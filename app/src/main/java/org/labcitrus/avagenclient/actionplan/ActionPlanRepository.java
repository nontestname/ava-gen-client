package org.labcitrus.avagenclient.actionplan;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches ActionPlans from:
 *
 *   workspace/actionplan/{appId}_actionplan.json
 *
 * Expected JSON (example):
 * {
 *   "app_id": "hu.vmiklos.plees_tracker",
 *   "action_plans": {
 *     "accessStatistics": {
 *       "method_name": "accessStatistics",
 *       "steps": [ ... ]
 *     },
 *     "startSleep": {
 *       "method_name": "startSleep",
 *       "steps": [ ... ]
 *     }
 *   }
 * }
 */
public class ActionPlanRepository {

    private static final String TAG = "ActionPlanRepository";
    private static ActionPlanRepository INSTANCE;

    // appId -> (methodName -> ActionPlan)
    private final Map<String, Map<String, ActionPlan>> cache = new HashMap<>();
    private final Gson gson = new Gson();

    private ActionPlanRepository() {}

    public static synchronized ActionPlanRepository getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ActionPlanRepository();
        }
        return INSTANCE;
    }

    /**
     * Get an ActionPlan for (appId, methodName).
     * This will lazy-load and cache the JSON file for that appId.
     */
    public ActionPlan getPlan(AccessibilityService service,
                              String appId,
                              String methodName) {
        if (appId == null || methodName == null) {
            Log.w(TAG, "getPlan: appId or methodName is null");
            return null;
        }

        Map<String, ActionPlan> plansForApp = cache.get(appId);
        if (plansForApp == null) {
            plansForApp = loadPlansForApp(service, appId);
            cache.put(appId, plansForApp);
        }

        ActionPlan plan = plansForApp.get(methodName);
        if (plan == null) {
            Log.w(TAG, "getPlan: no plan for appId=" + appId +
                    " methodName=" + methodName);
        }
        return plan;
    }

    /**
     * Load all plans for a given appId from:
     *   filesDir/workspace/actionplan/{appId}_actionplan.json
     *
     * Adjust the base directory if your generator writes elsewhere.
     */
    private Map<String, ActionPlan> loadPlansForApp(AccessibilityService service,
                                                    String appId) {
        try {
            // workspace/actionplan under internal files dir
            File workspaceDir = new File(service.getFilesDir(), "workspace");
            File actionPlanDir = new File(workspaceDir, "actionplan");
            File jsonFile = new File(actionPlanDir, appId + "_actionplan.json");

            if (!jsonFile.exists()) {
                Log.w(TAG, "loadPlansForApp: file not found: " + jsonFile.getAbsolutePath());
                return Collections.emptyMap();
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(jsonFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            String json = sb.toString();
            Log.i(TAG, "loadPlansForApp: loading plans for appId=" + appId);

            // Parse top-level structure:
            // { "app_id": "...", "action_plans": { methodName -> ActionPlan } }
            ActionPlanFileWrapper wrapper = gson.fromJson(json, ActionPlanFileWrapper.class);

            if (wrapper == null || wrapper.actionPlans == null) {
                Log.w(TAG, "loadPlansForApp: wrapper/actionPlans is null for appId=" + appId);
                return Collections.emptyMap();
            }

            Map<String, ActionPlan> result = new HashMap<>();

            // Ensure map key and ActionPlan.methodName are consistent; if methodName is missing,
            // fall back to the map key.
            for (Map.Entry<String, ActionPlan> entry : wrapper.actionPlans.entrySet()) {
                String methodKey = entry.getKey();
                ActionPlan plan = entry.getValue();
                if (plan == null) continue;

                String planName = plan.getMethodName();
                if (planName == null || planName.isEmpty()) {
                    // If generator forgot method_name, use the JSON key
                    // (you can add a setter if you want to store it back).
                    planName = methodKey;
                }
                result.put(planName, plan);
            }

            return result;

        } catch (Exception e) {
            Log.e(TAG, "loadPlansForApp: error loading plans for appId=" + appId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Top-level JSON wrapper for one app's file.
     *
     * {
     *   "app_id": "hu.vmiklos.plees_tracker",
     *   "action_plans": { "methodName": { ... } }
     * }
     */
    private static class ActionPlanFileWrapper {
        @SerializedName("app_id")
        String appId;

        @SerializedName("action_plans")
        Map<String, ActionPlan> actionPlans;
    }
}