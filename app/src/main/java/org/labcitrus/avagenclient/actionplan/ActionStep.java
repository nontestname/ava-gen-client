package org.labcitrus.avagenclient.actionplan;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * One step in an ActionPlan.
 *
 * JSON example:
 * {
 *   "action": "click",
 *   "matchers": [
 *     { "type": "contentDescription", "value": "More options", "mode": "equalsIgnoreCase" }
 *   ],
 *   "text": null,
 *   "millis": null,
 *   "node_query": "withContentDescription(\"More options\")"
 * }
 */
public class ActionStep {

    // Raw "action" string from JSON: "sleep", "click", "input_text", ...
    @SerializedName("action")
    private String actionRaw;

    // Optional list of matchers (may be empty for SLEEP)
    @SerializedName("matchers")
    private List<StepMatcher> matchers;

    // For INPUT_TEXT or future text-related actions
    @SerializedName("text")
    private String text;

    // For SLEEP
    @SerializedName("millis")
    private Long millis;

    // String NodeQuery expression from generator (not used for matching right now,
    // but kept for debugging / future extensions).
    @SerializedName("node_query")
    private String nodeQuery;

    // ---- getters used by ActionPlanExecutor ----

    public String getActionRaw() {
        return actionRaw;
    }

    public ActionType getActionType() {
        if (actionRaw == null) {
            return ActionType.UNKNOWN;
        }
        // Normalize weird whitespace from server-generated JSON
        String normalized = actionRaw
                .replace("\u00A0", " ")   // NBSP
                .replace("\u200B", "")    // zero-width
                .trim()
                .toLowerCase();

        switch (normalized) {
            case "sleep":
                return ActionType.SLEEP;

            case "click":
                return ActionType.CLICK;

            // All aliases for entering text
            case "input":
            case "input_text":
            case "inputtext":
            case "input-text":
            case "type_text":
            case "type":
            case "enter_text":
                return ActionType.INPUT_TEXT;

            case "scroll":
                return ActionType.SCROLL;

            // Aliases for scroll-down
            case "scroll_down":
            case "scroll-down":
            case "scrolldown":
                return ActionType.SCROLL_DOWN;

            // Aliases for swipe-left
            case "swipe_left":
            case "swipe-left":
            case "swipeleft":
                return ActionType.SWIPE_LEFT;

            // Aliases for swipe-right
            case "swipe_right":
            case "swipe-right":
            case "swiperight":
                return ActionType.SWIPE_RIGHT;

            // Global back navigation
            case "global_back":
            case "back":
                return ActionType.GLOBAL_BACK;

            default:
                return ActionType.UNKNOWN;
        }
    }

    public List<StepMatcher> getMatchers() {
        return matchers == null ? Collections.emptyList() : matchers;
    }

    public String getText() {
        return text;
    }

    public Long getMillis() {
        return millis;
    }

    public String getNodeQuery() {
        return nodeQuery;
    }

    @Override
    public String toString() {
        return "ActionStep{" +
                "actionRaw='" + actionRaw + '\'' +
                ", matchers=" + matchers +
                ", text='" + text + '\'' +
                ", millis=" + millis +
                ", nodeQuery='" + nodeQuery + '\'' +
                '}';
    }
}