package org.labcitrus.avagenclient.core;

import static org.labcitrus.avagenclient.core.Utils.getNodeDetails;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import org.labcitrus.avagenclient.accessibility.AVAGenService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class NodeFinder {
    private static final AtomicInteger checkedNodeCount = new AtomicInteger(0); // Tracks nodes checked

    /**
     * Finds nodes that match the given queries.
     * - Retrieves all descendant nodes only if needed.
     * - Uses only direct parent for `withParent()`.
     * - Uses only direct children for `withChild()`.
     *
     * @param nodes   The list of AccessibilityNodeInfo nodes to search.
     * @param queries The dynamic filtering criteria.
     * @return A list of matched nodes.
     */
    public static List<AccessibilityNodeInfo> findNodes(List<AccessibilityNodeInfo> nodes, NodeQuery... queries) {
        checkedNodeCount.set(0); // Reset counter

        boolean requiresDescendantSearch = requiresDescendantQuery(queries);
        Log.i(AVAGenService.TAG, "findNodes: Requires Descendant Search: " + requiresDescendantSearch);

        logNonNullQueries(queries); // Log all non-null queries before matching

        return nodes.stream()
                .filter(Objects::nonNull)
                .flatMap(node -> (requiresDescendantSearch ? getAllNodes(node) : List.of(node)).stream()) // Retrieve all nodes only if needed
                // .peek(node -> Log.i(AVAGenService.TAG, "Checking node: " + getNodeDetails(node) + " (Checked Count: " + checkedNodeCount.incrementAndGet() + ")"))
                .filter(node -> List.of(queries).stream()
                        .filter(Objects::nonNull)
                        .allMatch(query -> query.matches(node)))
                .peek(node -> Log.i(AVAGenService.TAG, "Matched node: " + getNodeDetails(node)))
                .collect(Collectors.toList());
    }

    /**
     * Determines if any of the queries require searching through descendants.
     */
    private static boolean requiresDescendantQuery(NodeQuery[] queries) {
        return List.of(queries).stream().anyMatch(NodeQuery::isDescendantQuery);
    }

    /**
     * Recursively retrieves all nodes (self + descendants).
     */
    public static List<AccessibilityNodeInfo> getAllNodes(AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        allNodes.add(node);
        IntStream.range(0, node.getChildCount())
                .mapToObj(node::getChild)
                .filter(child -> child != null)
                .forEach(child -> allNodes.addAll(getAllNodes(child)));
        return allNodes;
    }


    /**
     * Utility: match a node's viewIdResourceName against an expected id.
     * <p>
     * This allows matching either the full resource name (e.g., "android:id/button1")
     * or the simple id (e.g., "button1"), so action-plan matchers can just use the
     * short id from Espresso-like tests.
     *
     * @param node       The node whose ID should be checked.
     * @param expectedId The expected ID (full or simple).
     * @param mode       The matching mode string (e.g., "equalsIgnoreCase").
     * @return true if the node's ID matches under the given mode, false otherwise.
     */
    public static boolean matchesViewId(AccessibilityNodeInfo node, String expectedId, String mode) {
        if (node == null || expectedId == null) {
            return false;
        }

        String nodeId = node.getViewIdResourceName(); // e.g. "android:id/button1"
        if (nodeId == null) {
            return false;
        }

        // Simple name after the last '/', e.g. "button1"
        String simpleId = nodeId;
        int slashIdx = nodeId.lastIndexOf('/');
        if (slashIdx >= 0 && slashIdx + 1 < nodeId.length()) {
            simpleId = nodeId.substring(slashIdx + 1);
        }

        String expected = expectedId;
        String modeNormalized = mode != null ? mode : "equalsIgnoreCase";

        switch (modeNormalized) {
            case "equals":
                return nodeId.equals(expected) || simpleId.equals(expected);
            case "contains":
                return nodeId.contains(expected) || simpleId.contains(expected);
            case "containsIgnoreCase": {
                String nodeLower = nodeId.toLowerCase();
                String simpleLower = simpleId.toLowerCase();
                String expectedLower = expected.toLowerCase();
                return nodeLower.contains(expectedLower) || simpleLower.contains(expectedLower);
            }
            case "equalsIgnoreCase":
            default:
                return nodeId.equalsIgnoreCase(expected) || simpleId.equalsIgnoreCase(expected);
        }
    }

    /**
     * Logs all non-null queries before matching.
     */
    private static void logNonNullQueries(NodeQuery[] queries) {
        List.of(queries).stream()
                .filter(query -> query != null)
                .forEach(query -> Log.i(AVAGenService.TAG, "Pre-matching query: " + query));
    }
}