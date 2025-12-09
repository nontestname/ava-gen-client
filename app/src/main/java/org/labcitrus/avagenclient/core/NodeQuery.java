package org.labcitrus.avagenclient.core;

import static org.labcitrus.avagenclient.core.Utils.getNodeDetails;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import org.labcitrus.avagenclient.accessibility.AVAGenService;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.List;


/**
 * Represents a query used to filter AccessibilityNodeInfo objects based on various attributes
 * such as ID, text, class name, content description, and relationships (parent, child, descendant).
 * It uses Java's Predicate<T> functional interface to define conditions dynamically.
 *
 * <p>Example Usage:</p>
 *
 * <pre>{@code
 * // Example 1: Match nodes by ID
 * List<AccessibilityNodeInfo> nodes = NodeFinder.findNodes(nodesToSearch,
 *     NodeQuery.withId("submit_button")
 * );
 *
 * // Example 2: Match nodes whose parent has ID "container"
 * List<AccessibilityNodeInfo> nodesWithParent = NodeFinder.findNodes(nodesToSearch,
 *     NodeQuery.withParent(NodeQuery.withId("container"))
 * );
 *
 * // Example 3: Match nodes that have a child with text "Click Me"
 * List<AccessibilityNodeInfo> nodesWithChild = NodeFinder.findNodes(nodesToSearch,
 *     NodeQuery.withChild(NodeQuery.withText("Click Me"))
 * );
 *
 * // Example 4: Match nodes that have a descendant with class name "LinearLayout"
 * List<AccessibilityNodeInfo> nodesWithDescendant = NodeFinder.findNodes(nodesToSearch,
 *     NodeQuery.withDescendant(NodeQuery.withClassName("LinearLayout"))
 * );
 *
 * // Example 5: Combined query - Match nodes with ID "button123" and whose parent has class "FrameLayout"
 * List<AccessibilityNodeInfo> complexQuery = NodeFinder.findNodes(nodesToSearch,
 *     NodeQuery.withId("button123")
 *         .and(NodeQuery.withParent(NodeQuery.withClassName("FrameLayout")))
 * );
 * }</pre>
 */
public class NodeQuery {

    /**
     * A predicate that defines the condition for matching an AccessibilityNodeInfo node.
     * This function takes a node as input and returns true if it matches the query.
     */
    private final Predicate<AccessibilityNodeInfo> condition;

    /**
     * A flag that indicates whether this query involves searching for a descendant node.
     * If true, a recursive search through all child nodes is performed.
     */
    private final boolean isDescendantQuery;

    // [NEW] Debug label to improve logging/toString without changing matching semantics.
    private final String debug;

    /**
     * Private constructor for NodeQuery. Initializes the query with a given condition.
     *
     * @param condition        A Predicate function that determines whether a node matches.
     * @param isDescendantQuery True if the query requires recursive descendant search.
     */
    private NodeQuery(Predicate<AccessibilityNodeInfo> condition, boolean isDescendantQuery, String debug) {
        this.condition = condition;
        this.isDescendantQuery = isDescendantQuery;
        this.debug = debug;
    }

    // ----------------------------------------------------------------------
    // [NEW] StringMatcher-based overloads to support expressions like
    //       containsStringIgnoringCase("...") across ID/Text/Class/ContentDesc.
    //       Existing String-based methods below remain as convenience wrappers.
    // ----------------------------------------------------------------------

    public static NodeQuery withId(StringMatcher matcher) {
        return new NodeQuery(node -> {
            if (node == null || matcher == null) {
                Log.i(AVAGenService.TAG, "withId: node or matcher is null -> false");
                return false;
            }

            String nodeId = node.getViewIdResourceName();
            String simpleId = null;

            if (nodeId != null) {
                int slashIndex = nodeId.lastIndexOf('/');
                simpleId = (slashIndex >= 0 && slashIndex < nodeId.length() - 1)
                        ? nodeId.substring(slashIndex + 1)
                        : nodeId;
            }

            boolean result = false;

            if (nodeId != null && matcher.test(nodeId)) {
                result = true;
            } else if (simpleId != null && matcher.test(simpleId)) {
                result = true;
            }

            Log.i(
                    AVAGenService.TAG,
                    "withId: nodeId=" + nodeId + " simpleId=" + simpleId + " matcher=" + matcher + " -> " + result
            );
            return result;
        }, false, "withId[" + matcher + "]");
    }

    public static NodeQuery withText(StringMatcher matcher) {
        return new NodeQuery(node -> {
            CharSequence cs = node.getText();
            String nodeText = cs != null ? cs.toString() : null;
            boolean result = nodeText != null && matcher.test(nodeText);
            Log.i(AVAGenService.TAG, "withText: nodeText=" + nodeText + " matcher=" + matcher + " -> " + result);
            return result;
        }, false, "withText[" + matcher + "]");
    }

    public static NodeQuery withClassName(StringMatcher matcher) {
        return new NodeQuery(node -> {
            CharSequence cs = node.getClassName();
            String nodeClass = cs != null ? cs.toString() : null;
            boolean result = nodeClass != null && matcher.test(nodeClass);
            Log.i(AVAGenService.TAG, "withClassName: nodeClass=" + nodeClass + " matcher=" + matcher + " -> " + result);
            return result;
        }, false, "withClassName[" + matcher + "]");
    }

    public static NodeQuery withContentDescription(StringMatcher matcher) {
        return new NodeQuery(node -> {
            CharSequence cs = node.getContentDescription();
            String nodeDesc = cs != null ? cs.toString() : null;
            boolean result = nodeDesc != null && matcher.test(nodeDesc);
            Log.i(AVAGenService.TAG, "withContentDesc: nodeDesc=" + nodeDesc + " matcher=" + matcher + " -> " + result);
            return result;
        }, false, "withContentDesc[" + matcher + "]");
    }


    // [NEW] Matches nodes whose checked state is false (e.g., unchecked radio/checkbox/toggle).
    public static NodeQuery isNotChecked() {
        return new NodeQuery(node -> {
            boolean result = node != null && !node.isChecked();
            Log.i(AVAGenService.TAG, "isNotChecked: " + getNodeDetails(node) + " -> " + result);
            return result;
        }, false, "isNotChecked()");
    }

    // [NEW - optional] Symmetry helper: matches nodes whose checked state is true.
    public static NodeQuery isChecked() {
        return new NodeQuery(node -> {
            boolean result = node != null && node.isChecked();
            Log.i(AVAGenService.TAG, "isChecked: " + getNodeDetails(node) + " -> " + result);
            return result;
        }, false, "isChecked()");
    }

    /**
     * Creates a NodeQuery that matches nodes based on their View ID.
     * <p>
     * The matcher is applied to both the fully-qualified resource name (e.g. {@code "android:id/button1"})
     * and the simple ID after the slash (e.g. {@code "button1"}), using a case-insensitive, substring match.
     *
     * @param id The ID fragment to match (full or simple ID).
     * @return A NodeQuery instance that filters nodes by ID.
     */
    public static NodeQuery withId(String id) {
        return withId(StringMatcher.containsIgnoreCase(id));
    }

    /**
     * Creates a NodeQuery that matches nodes based on their text content (case-insensitive, substring match).
     *
     * @param text The text to match.
     * @return A NodeQuery instance that filters nodes by text.
     */
    public static NodeQuery withText(String text) {
        return withText(StringMatcher.containsIgnoreCase(text));
    }

    /**
     * Creates a NodeQuery that matches nodes whose text exactly equals the given text,
     * ignoring case (case-insensitive).
     *
     * @param text The text to match.
     * @return A NodeQuery instance that filters nodes by exact text.
     */
    public static NodeQuery isTextIgnoreCase(String text) {
        return withText(StringMatcher.equalsIgnoreCase(text));
    }

    /**
     * Creates a NodeQuery that matches nodes based on their class name (case-insensitive, substring match).
     *
     * @param className The class name to match.
     * @return A NodeQuery instance that filters nodes by class name.
     */
    public static NodeQuery withClassName(String className) {
        return withClassName(StringMatcher.containsIgnoreCase(className));
    }

    /**
     * Creates a NodeQuery that matches nodes based on their content description
     * (case-insensitive, substring match).
     *
     * @param contentDesc The content description to match.
     * @return A NodeQuery instance that filters nodes by content description.
     */
    public static NodeQuery withContentDescription(String contentDesc) {
        return withContentDescription(StringMatcher.containsIgnoreCase(contentDesc));
    }

    /**
     * Creates a NodeQuery that matches nodes whose parent matches the given conditions.
     *
     * @param conditions The conditions that the parent node must satisfy.
     * @return A NodeQuery instance that filters nodes based on their parent.
     */
    public static NodeQuery withParent(NodeQuery... conditions) {
        return new NodeQuery(node -> {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent == null) return false;
            boolean result = List.of(conditions).stream().allMatch(query -> query.matches(parent));
            Log.i(AVAGenService.TAG, "withParent: Checking parent of " + getNodeDetails(node) + " -> " + result);
            return result;
        }, false, "withParent(" + List.of(conditions) + ")");
    }

    /**
     * Creates a NodeQuery that matches nodes based on their index within the parent.
     *
     * <p>Usage Example:</p>
     * <pre>{@code
     * // Find a node that is the second child (index 1) of its parent
     * List<AccessibilityNodeInfo> nodes = findNode(
     *     withParentIndex(1)
     * );
     * }</pre>
     *
     * @param index The expected index of the node within its parent.
     * @return A NodeQuery instance that filters nodes by parent index.
     */
    public static NodeQuery withParentIndex(int index) {
        return new NodeQuery(node -> {
            int nodeIndex = getNodeIndex(node);
            boolean result = (nodeIndex == index);
            Log.i(AVAGenService.TAG, "withParentIndex: Checking node " + getNodeDetails(node)
                    + " (Expected Index: " + index + ", Actual Index: " + nodeIndex + ") -> " + result);
            return result;
        }, false, "withParentIndex(" + index + ")");
    }

    /**
     * Creates a NodeQuery that matches nodes that have a direct child meeting the given conditions.
     *
     * @param conditions The conditions that at least one child must satisfy.
     * @return A NodeQuery instance that filters nodes based on their children.
     */
    public static NodeQuery withChild(NodeQuery... conditions) {
        return new NodeQuery(node -> {
            boolean result = IntStream.range(0, node.getChildCount())
                    .mapToObj(node::getChild)
                    .filter(child -> child != null)
                    .anyMatch(child -> List.of(conditions).stream().allMatch(query -> query.matches(child)));
            Log.i(AVAGenService.TAG, "withChild: Checking children of " + getNodeDetails(node) + " -> " + result);
            return result;
        }, false, "withChild(" + List.of(conditions) + ")");
    }

    /**
     * Creates a NodeQuery that matches nodes that have at least one descendant
     * (any depth) meeting the given conditions.
     *
     * @param conditions The conditions that at least one descendant must satisfy.
     * @return A NodeQuery instance that filters nodes based on their descendants.
     */
    public static NodeQuery hasDescendant(NodeQuery... conditions) {
        return new NodeQuery(node -> {
            boolean result = NodeFinder.getAllNodes(node).stream()
                    .anyMatch(descendant -> List.of(conditions).stream().allMatch(query -> query.matches(descendant)));
            Log.i(AVAGenService.TAG, "withDescendant: Checking descendants of " + getNodeDetails(node) + " -> " + result);
            return result;
        }, true, "withDescendant(" + List.of(conditions) + ")");
    }

    /**
     * Parses a string expression into a StringMatcher.
     *
     * Supported forms:
     *   - "literal"  -> containsIgnoreCase("literal")
     *   - equals("YES")
     *   - equalsIgnoreCase("YES")
     *   - containsIgnoreCase("foo")
     *   - containsStringIgnoringCase("EditText")
     *   - contains("bar")
     *   - startsWithIgnoreCase("prefix")
     *   - endsWithIgnoreCase("suffix")
     *
     * Any unrecognized expression falls back to containsIgnoreCase on the raw value.
     */
    private static StringMatcher parseStringMatcher(String raw) {
        if (raw == null) {
            return null;
        }

        String expr = raw.trim();
        if (expr.isEmpty()) {
            return null;
        }

        // 1) If it's a bare quoted string, treat it as a simple containsIgnoreCase literal.
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) {
            String value = expr.substring(1, expr.length() - 1);
            return StringMatcher.containsIgnoreCase(value);
        }

        // 2) Helper calls, e.g., equals("YES"), containsIgnoreCase("foo"), etc.
        if (expr.startsWith("equalsIgnoreCase(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "equalsIgnoreCase(");
            return StringMatcher.equalsIgnoreCase(inner);
        }

        if (expr.startsWith("equals(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "equals(");
            // You can choose exact match with or without case sensitivity;
            // here we default to case-insensitive equality.
            return StringMatcher.equalsIgnoreCase(inner);
        }

        if (expr.startsWith("containsIgnoreCase(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "containsIgnoreCase(");
            return StringMatcher.containsIgnoreCase(inner);
        }

        if (expr.startsWith("containsStringIgnoringCase(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "containsStringIgnoringCase(");
            // Map containsStringIgnoringCase(...) to the same runtime matcher
            // as containsIgnoreCase(...).
            return StringMatcher.containsIgnoreCase(inner);
        }

        if (expr.startsWith("contains(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "contains(");
            return StringMatcher.containsIgnoreCase(inner);
        }

        if (expr.startsWith("startsWithIgnoreCase(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "startsWithIgnoreCase(");
            return StringMatcher.startsWithIgnoreCase(inner);
        }

        if (expr.startsWith("endsWithIgnoreCase(") && expr.endsWith(")")) {
            String inner = extractInnerString(expr, "endsWithIgnoreCase(");
            return StringMatcher.endsWithIgnoreCase(inner);
        }

        // 3) Fallback: treat the raw expression as a literal and use containsIgnoreCase.
        return StringMatcher.containsIgnoreCase(expr);
    }

    /**
     * Extracts the inner string literal from an expression of the form:
     *   prefix + "..." + ")"
     *
     * For example:
     *   extractInnerString("equalsIgnoreCase(\"YES\")", "equalsIgnoreCase(")
     *   -> "YES"
     */
    private static String extractInnerString(String expr, String prefix) {
        String inner = expr.substring(prefix.length(), expr.length() - 1).trim();
        if (inner.startsWith("\"") && inner.endsWith("\"") && inner.length() >= 2) {
            inner = inner.substring(1, inner.length() - 1);
        }
        return inner;
    }

    /**
     * Parses a simple comma‑separated list of NodeQuery expressions such as:
     *   withText("YES"), withId("button1"), withParent(withId("Amount"))
     *
     * This parser is *paren-aware*:
     * it splits on commas that are not inside parentheses, so nested expressions like
     *   withClassName(containsStringIgnoringCase("EditText"))
     * are kept intact as a single part.
     *
     * For argument strings (e.g., the "YES" or the containsIgnoreCase(...) expression),
     * we route them through parseStringMatcher(...) so that helper forms like:
     *   equals("YES"), equalsIgnoreCase("YES"), containsIgnoreCase("foo"),
     *   containsStringIgnoringCase("EditText")
     * are correctly mapped to the corresponding StringMatcher.
     */
    public static NodeQuery[] parseList(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            return new NodeQuery[0];
        }

        // Split on commas that are not inside parentheses.
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int depth = 0;

        for (char c : expr.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }

            if (c == ',' && depth == 0) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString().trim());

        List<NodeQuery> queries = new ArrayList<>();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (part.startsWith("withId(") && part.endsWith(")")) {
                String inner = part.substring("withId(".length(), part.length() - 1).trim();
                StringMatcher m = parseStringMatcher(inner);
                if (m != null) {
                    queries.add(NodeQuery.withId(m));
                }

            } else if (part.startsWith("withText(") && part.endsWith(")")) {
                String inner = part.substring("withText(".length(), part.length() - 1).trim();
                StringMatcher m = parseStringMatcher(inner);
                if (m != null) {
                    queries.add(NodeQuery.withText(m));
                }

            } else if (part.startsWith("withContentDescription(") && part.endsWith(")")) {
                String inner = part.substring("withContentDescription(".length(), part.length() - 1).trim();
                StringMatcher m = parseStringMatcher(inner);
                if (m != null) {
                    queries.add(NodeQuery.withContentDescription(m));
                }

            } else if (part.startsWith("withClassName(") && part.endsWith(")")) {
                String inner = part.substring("withClassName(".length(), part.length() - 1).trim();
                StringMatcher m = parseStringMatcher(inner);
                if (m != null) {
                    queries.add(NodeQuery.withClassName(m));
                }

            // Nested structural queries
            } else if (part.startsWith("withParent(") && part.endsWith(")")) {
                String inner = part.substring("withParent(".length(), part.length() - 1);
                NodeQuery[] innerQ = parseList(inner);
                queries.add(NodeQuery.withParent(innerQ));

            } else if (part.startsWith("withChild(") && part.endsWith(")")) {
                String inner = part.substring("withChild(".length(), part.length() - 1);
                NodeQuery[] innerQ = parseList(inner);
                queries.add(NodeQuery.withChild(innerQ));

            } else if (part.startsWith("hasDescendant(") && part.endsWith(")")) {
                String inner = part.substring("hasDescendant(".length(), part.length() - 1);
                NodeQuery[] innerQ = parseList(inner);
                queries.add(NodeQuery.hasDescendant(innerQ));

            } else if (part.equals("isNotChecked()")) {
                // Allow isNotChecked() in the DSL as a simple state predicate.
                queries.add(NodeQuery.isNotChecked());

            } else if (part.equals("isChecked()")) {
                // Optional: explicit checked-state predicate.
                queries.add(NodeQuery.isChecked());
            }
        }

        return queries.toArray(new NodeQuery[0]);
    }

    /**
     * Gets the index of an AccessibilityNodeInfo within its parent.
     *
     * @param node The AccessibilityNodeInfo whose index is needed.
     * @return The index of the node within its parent, or -1 if it has no parent or is not found.
     */
    private static int getNodeIndex(AccessibilityNodeInfo node) {
        if (node == null || node.getParent() == null) {
            return -1; // No parent, so no index
        }

        AccessibilityNodeInfo parent = node.getParent();
        int childCount = parent.getChildCount();

        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child != null && child.equals(node)) {
                return i; // Found the node's index
            }
        }

        return -1; // Node not found among parent's children
    }

    /**
     * Checks if the given node matches the query condition.
     *
     * @param node The AccessibilityNodeInfo node to evaluate.
     * @return True if the node matches the condition, false otherwise.
     */
    public boolean matches(AccessibilityNodeInfo node) {
        return condition.test(node);
    }

    /**
     * Returns whether this query requires recursive descendant searching.
     *
     * @return True if this query searches for descendants, false otherwise.
     */
    public boolean isDescendantQuery() {
        return isDescendantQuery;
    }


    /**
     * Returns a string representation of this NodeQuery.
     *
     * @return A string describing the query.
     */
    @Override
    public String toString() {
        return "NodeQuery{" + debug + ", descendant=" + isDescendantQuery + "}";
    }
}