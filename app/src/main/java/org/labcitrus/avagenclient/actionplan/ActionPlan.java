package org.labcitrus.avagenclient.actionplan;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * One VA method's plan:
 *
 * JSON (inside "action_plans"):
 * "accessStatistics": {
 *   "method_name": "accessStatistics",
 *   "steps": [ { ... }, { ... } ]
 * }
 */
public class ActionPlan {

    // "method_name": "accessStatistics"
    @SerializedName("method_name")
    private String methodName;

    // "steps": [ { ... }, { ... } ]
    @SerializedName("steps")
    private List<ActionStep> steps;

    public String getMethodName() {
        return methodName;
    }

    public List<ActionStep> getSteps() {
        return steps == null ? Collections.emptyList() : steps;
    }

    public boolean isEmpty() {
        return getSteps().isEmpty();
    }

    @Override
    public String toString() {
        return "ActionPlan{" +
                "methodName='" + methodName + '\'' +
                ", steps=" + steps +
                '}';
    }
}