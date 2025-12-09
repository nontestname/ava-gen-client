package org.labcitrus.avagenclient.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * ToolCallManager maintains per-session state for a pending tool call and its JSON payload.
 */
public class ToolCallManager {
    private boolean pendingToolCall = false;
    private String pendingActionPlanJson = null;

    /**
     * Checks if there is a pending tool/function call awaiting user confirmation.
     * @return true if pending, false otherwise
     */
    public boolean hasPendingToolCall() {
        return pendingToolCall;
    }

    /**
     * Sets a new pending action plan JSON string.
     * @param json the JSON payload of the action plan
     */
    public void setPendingActionPlan(String json) {
        this.pendingToolCall = true;
        this.pendingActionPlanJson = (json != null ? json : "");
    }

    /**
     * Clears the current pending tool call status and JSON payload.
     */
    public void clear() {
        this.pendingToolCall = false;
        this.pendingActionPlanJson = null;
    }

    /**
     * Returns the pending action plan JSON string.
     */
    public String getPendingActionPlanJson() {
        return pendingActionPlanJson;
    }
}