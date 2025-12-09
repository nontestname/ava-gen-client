package org.labcitrus.avagenclient.actionplan;

/**
 * Enum for known action types.
 *
 * The string values must match what the server/parser emits
 * (e.g., "sleep", "click", "input_text", etc.).
 */
public enum ActionType {
    // timing
    SLEEP("sleep", false),

    // node-based actions
    CLICK("click", true),
    INPUT_TEXT("input_text", true),
    SCROLL("scroll", true),          // generic node scroll if you use it

    // global actions
    GLOBAL_BACK("global_back", false),

    // gesture / screen-level actions
    SCROLL_DOWN("scroll_down", false),
    SWIPE_LEFT("swipe_left", false),
    SWIPE_RIGHT("swipe_right", false),
    SWIPE_LEFT_50("swipe_left_50", false),
    SWIPE_RIGHT_50("swipe_right_50", false),

    // fallback
    UNKNOWN("unknown", false);

    private final String raw;
    private final boolean requiresNode;

    ActionType(String raw, boolean requiresNode) {
        this.raw = raw;
        this.requiresNode = requiresNode;
    }

    /**
     * Raw string as used in JSON ("click", "sleep", etc.).
     */
    public String getRaw() {
        return raw;
    }

    /**
     * Whether this action requires a target AccessibilityNodeInfo.
     */
    public boolean requiresNode() {
        return requiresNode;
    }

    /**
     * Map a raw JSON action string to an enum value.
     */
    public static ActionType fromRaw(String raw) {
        if (raw == null) return UNKNOWN;
        for (ActionType t : values()) {
            if (t.raw.equalsIgnoreCase(raw)) {
                return t;
            }
        }
        return UNKNOWN;
    }
}