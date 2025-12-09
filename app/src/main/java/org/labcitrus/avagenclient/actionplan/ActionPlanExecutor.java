package org.labcitrus.avagenclient.actionplan;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.labcitrus.avagenclient.core.ActionPerformer;
import org.labcitrus.avagenclient.core.NodeFinder;
import org.labcitrus.avagenclient.core.NodeQuery;
import org.labcitrus.avagenclient.core.StringMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes an ActionPlan step-by-step.
 *
 * JSON → ActionPlan → ActionStep → StepMatcher → NodeQuery → NodeFinder → AccessibilityNodeInfo → ActionPerformer
 *
 * Compatible with JSON shape:
 * {
 *   "app_id": "...",
 *   "action_plans": {
 *      "methodName": {
 *          "method_name": "...",
 *          "steps": [
 *              {
 *                 "action": "click",
 *                 "matchers": [
 *                    {"type": "contentDescription","value": "More options","mode": "equalsIgnoreCase"}
 *                 ],
 *                 "node_query": "withContentDescription(\"More options\")",
 *                 ...
 *              }
 *           ]
 *       }
 *   }
 * }
 *
 * See example: hu.vmiklos.plees_tracker_actionplan.json
 */
public class ActionPlanExecutor {

    private static final String TAG = "ActionPlanExecutor";
    private static final long DEFAULT_POST_ACTION_DELAY_MS = 1000L;

    /** The AccessibilityService instance is the bridge for:
     *   - getRootInActiveWindow()
     *   - performGlobalAction()
     *   - dispatchGesture()
     */
    private final AccessibilityService service;

    /** Performs actual UI events: click, input, scroll, back */
    private final ActionPerformer performer;

    public ActionPlanExecutor(AccessibilityService service) {
        this.service = service;
        this.performer = new ActionPerformer(service);
    }

    /**
     * Execute all steps for one VA method.
     * Example from JSON:
     *   "startSleep": { "steps": [...] }
     */
    public void executePlan(String appId, ActionPlan plan) {
        if (plan == null) {
            Log.w(TAG, "executePlan: plan is null for appId=" + appId);
            return;
        }
        if (plan.isEmpty()) {
            Log.w(TAG, "executePlan: empty plan for appId=" + appId +
                    " method=" + plan.getMethodName());
            return;
        }

        Log.i(TAG, "executePlan: appId=" + appId +
                " method=" + plan.getMethodName() +
                " steps=" + plan.getSteps().size());

        List<ActionStep> steps = plan.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            ActionStep step = steps.get(i);
            Log.i(TAG, "executePlan: step[" + i + "] = " + step);
            executeStep(step);

            // Auto-wait logic after each step, with special handling for scroll actions.
            if (i < steps.size() - 1) {
                ActionStep next = steps.get(i + 1);
                String nextRaw = (next != null) ? next.getActionRaw() : null;
                boolean nextIsSleep = nextRaw != null && nextRaw.equalsIgnoreCase("sleep");

                // Determine if current action is scroll-related
                String currentAction = (step != null ? step.getActionRaw() : null);
                boolean isScroll = false;
                if (currentAction != null) {
                    String lower = currentAction.toLowerCase();
                    isScroll = lower.contains("scroll") || lower.contains("scrolldown") || lower.contains("scrollup");
                }

                if (!nextIsSleep) {
                    long delay = DEFAULT_POST_ACTION_DELAY_MS;

                    // Add an additional 2 seconds after scroll operations
                    if (isScroll) {
                        delay += 2000;
                        Log.i(TAG, "executePlan: scroll action detected — extending wait by 2000 ms");
                        sleepSafely(DEFAULT_POST_ACTION_DELAY_MS+delay);
                    }

                    if (!nextIsSleep) {
                        Log.i(TAG, "executePlan: auto-wait " + DEFAULT_POST_ACTION_DELAY_MS + " ms before next step");
                        sleepSafely(DEFAULT_POST_ACTION_DELAY_MS);
                    }
                }
            }
        }
    }

    /**
     * Execute a single step.
     * JSON: step["action"] → enum ActionType
     */
    private void executeStep(ActionStep step) {
        if (step == null) {
            Log.w(TAG, "executeStep: step is null");
            return;
        }

        ActionType type = step.getActionType();
        Log.i(TAG, "executeStep: actionType=" + type + " raw=" + step.getActionRaw());

        switch (type) {
            case SLEEP:
                executeSleep(step);
                break;
            case CLICK:
                executeClick(step);
                break;
            case INPUT_TEXT:
                executeInputText(step);
                break;
            case SCROLL:
                executeScroll(step);
                break;
            case SCROLL_DOWN:
                executeScrollDown(step);
                break;
            case SWIPE_LEFT:
                executeSwipeLeft(step);
                break;
            case SWIPE_RIGHT:
                executeSwipeRight(step);
                break;
            case GLOBAL_BACK:
                executeGlobalBack(step);
                break;

            case UNKNOWN:
            default:
                Log.w(TAG, "executeStep: UNKNOWN action for step=" + step);
                break;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // region: individual action handlers
    // ---------------------------------------------------------------------------------------------

    private void executeSleep(ActionStep step) {
        Long millis = step.getMillis();
        long duration = (millis != null && millis > 0) ? millis : 1000L;

        // Example: {"action":"sleep","millis":2000}
        Log.i(TAG, "executeSleep: millis=" + duration);

        sleepSafely(duration);
    }

    private void executeClick(ActionStep step) {
        AccessibilityNodeInfo node = findNodeForStep(step);
        if (node == null) {
            Log.w(TAG, "executeClick: no node found for step=" + step);
            return;
        }
        performer.performClick(node);
    }

    private void executeInputText(ActionStep step) {
        AccessibilityNodeInfo node = findNodeForStep(step);
        if (node == null) {
            Log.w(TAG, "executeInputText: no node found for step=" + step);
            return;
        }
        String text = step.getText();
        if (text == null) {
            Log.w(TAG, "executeInputText: text is null for step=" + step);
            return;
        }
        boolean ok = performer.performInput(node, text);
        Log.i(TAG, "executeInputText: result=" + ok + " text=" + text);
    }

    private void executeScroll(ActionStep step) {
        Log.i(TAG, "executeScroll: executing generic scrollDown()");
        performer.performScrollDown();
    }

    private void executeScrollDown(ActionStep step) {
        Log.i(TAG, "executeScrollDown: scrollDown()");
        performer.performScrollDown();
    }

    private void executeSwipeLeft(ActionStep step) {
        Log.i(TAG, "executeSwipeLeft: full-screen swipe left");
        performer.performSwipeLeft();
    }

    private void executeSwipeRight(ActionStep step) {
        Log.i(TAG, "executeSwipeRight: full-screen swipe right");
        performer.performSwipeRight();
    }

    private void executeGlobalBack(ActionStep step) {
        Log.i(TAG, "executeGlobalBack: pressBack()");
        performer.pressBack();
    }

    // ---------------------------------------------------------------------------------------------
    // region: node resolution
    // ---------------------------------------------------------------------------------------------

    /**
     * Resolve a target node for the given step.
     *
     * Priority:
     *  1) If step.node_query is present (string DSL), parse it via NodeQuery.parseList(...)
     *     and use the full, possibly nested query:
     *
     *     Examples (from server JSON):
     *       "node_query": "withText(\"YES\"), withId(\"button1\")"
     *       "node_query": "withParent(withId(\"Amount\"), hasDescendant(withId(\"TaType\")))"
     *
     *  2) Otherwise, fall back to the flat "matchers" array and build a conjunction
     *     of NodeQuery predicates:
     *
     *     Examples:
     *       "matchers": [
     *         { "type": "text", "value": "Statistics", "mode": "equalsIgnoreCase" },
     *         { "type": "id",   "value": "title",      "mode": "equalsIgnoreCase" }
     *       ]
     *
     * JSON → ActionStep.node_query / matchers → NodeQuery[] → NodeFinder → AccessibilityNodeInfo.
     */
    private AccessibilityNodeInfo findNodeForStep(ActionStep step) {
        if (step == null) {
            Log.w(TAG, "findNodeForStep: step is null");
            return null;
        }

        // 1) Prefer the node_query DSL if provided (this can express nested structure using
        //    withParent(...), withChild(...), hasDescendant(...), etc.).
        NodeQuery[] queries = null;
        String nodeQueryExpr = step.getNodeQuery();
        if (nodeQueryExpr != null && !nodeQueryExpr.trim().isEmpty()) {
            Log.i(TAG, "findNodeForStep: using node_query=" + nodeQueryExpr);
            queries = NodeQuery.parseList(nodeQueryExpr);

            if (queries == null || queries.length == 0) {
                Log.w(TAG, "findNodeForStep: NodeQuery.parseList produced no queries, will fall back to matchers.");
            }
        }

        // 2) If node_query is missing or failed to parse, fall back to the flat matchers.
        if (queries == null || queries.length == 0) {
            List<StepMatcher> matchers = step.getMatchers();
            if (matchers == null || matchers.isEmpty()) {
                Log.w(TAG, "findNodeForStep: no node_query and no matchers for step=" + step);
                return null;
            }

            Log.i(TAG, "findNodeForStep: building NodeQuery from matchers=" + matchers);
            queries = buildQueriesFromMatchers(matchers);

            if (queries.length == 0) {
                Log.w(TAG, "findNodeForStep: no valid NodeQuery built for step=" + step);
                return null;
            }
        }

        // 3) Execute the NodeQuery chain against the current accessibility tree.
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "findNodeForStep: rootInActiveWindow is null");
            return null;
        }

        List<AccessibilityNodeInfo> allNodes = NodeFinder.getAllNodes(root);
        List<AccessibilityNodeInfo> matches = NodeFinder.findNodes(allNodes, queries);

        if (matches.isEmpty()) {
            Log.w(TAG, "findNodeForStep: no matching nodes for step=" + step);
            return null;
        }

        AccessibilityNodeInfo node = matches.get(0);
        Log.i(TAG, "findNodeForStep: found node=" + node);
        return node;
    }

    /**
     * Fallback: build a flat conjunction of NodeQuery predicates from the "matchers" array
     * when no node_query DSL is present on the step.
     *
     * Example matchers from JSON:
     *   {"type":"id","value":"title","mode":"equalsIgnoreCase"}
     *   {"type":"text","value":"Statistics","mode":"equalsIgnoreCase"}
     *
     * These become:
     *   NodeQuery.withId(equalsIgnoreCase("title"))
     *   NodeQuery.withText(equalsIgnoreCase("Statistics"))
     * which are then combined by NodeFinder.findNodes(...).
     */
    private NodeQuery[] buildQueriesFromMatchers(List<StepMatcher> matchers) {
        List<NodeQuery> result = new ArrayList<>();

        for (StepMatcher m : matchers) {
            if (m == null) continue;

            String type = m.getType();     // "text", "id", "contentDescription"
            String value = m.getValue();   // "Statistics"
            String mode = m.getMode();     // equalsIgnoreCase, contains, etc.

            StringMatcher sm = toStringMatcher(value, mode);
            NodeQuery q = toNodeQuery(type, sm);

            if (q != null) {
                result.add(q);
            }
        }
        return result.toArray(new NodeQuery[0]);
    }

    /**
     * Map StepMatcher.mode + value → StringMatcher.
     */
    private StringMatcher toStringMatcher(String value, String mode) {
        if (mode == null) return StringMatcher.equalsIgnoreCase(value);

        switch (mode) {
            case "equals":
            case "equalsIgnoreCase":  return StringMatcher.equalsIgnoreCase(value);
            case "contains":
            case "containsIgnoreCase": return StringMatcher.containsIgnoreCase(value);
            case "startsWith":
            case "startsWithIgnoreCase": return StringMatcher.startsWithIgnoreCase(value);
            case "endsWith":
            case "endsWithIgnoreCase": return StringMatcher.endsWithIgnoreCase(value);
            case "regex": return StringMatcher.regex(value);

            default:
                Log.w(TAG, "toStringMatcher: unknown mode=" + mode + " → default equalsIgnoreCase");
                return StringMatcher.equalsIgnoreCase(value);
        }
    }

    /**
     * Convert matcher type → NodeQuery builder.
     * Supported types (matching JSON):
     *   text, id, contentDescription, className
     */
    private NodeQuery toNodeQuery(String type, StringMatcher matcher) {
        if (type == null || matcher == null) return null;

        switch (type) {
            case "text":               return NodeQuery.withText(matcher);
            case "id":                 return NodeQuery.withId(matcher);
            case "contentDescription": return NodeQuery.withContentDescription(matcher);
            case "className":          return NodeQuery.withClassName(matcher);
            default:
                Log.w(TAG, "toNodeQuery: unsupported matcher type=" + type);
                return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // region: utilities
    // ---------------------------------------------------------------------------------------------

    private void sleepSafely(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "sleepSafely: interrupted", e);
        }
    }
}