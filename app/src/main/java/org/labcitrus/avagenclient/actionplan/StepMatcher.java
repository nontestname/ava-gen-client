package org.labcitrus.avagenclient.actionplan;

import com.google.gson.annotations.SerializedName;

/**
 * One field matcher in a step.
 *
 * JSON example:
 * {
 *   "type": "text",
 *   "value": "Statistics",
 *   "mode": "equalsIgnoreCase"
 * }
 *
 * Supported types (used in ActionPlanExecutor.toNodeQuery):
 *   - "text"
 *   - "id"
 *   - "contentDescription"
 *   - "className"
 *
 * Supported modes (used in ActionPlanExecutor.toStringMatcher):
 *   - "equals", "equalsIgnoreCase"
 *   - "contains", "containsIgnoreCase"
 *   - "startsWith", "startsWithIgnoreCase"
 *   - "endsWith", "endsWithIgnoreCase"
 *   - "regex"
 */
public class StepMatcher {

    @SerializedName("type")
    private String type;

    @SerializedName("value")
    private String value;

    @SerializedName("mode")
    private String mode;

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "StepMatcher{" +
                "type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", mode='" + mode + '\'' +
                '}';
    }
}