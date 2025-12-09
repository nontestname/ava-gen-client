package org.labcitrus.avagenclient.core;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.labcitrus.avagenclient.accessibility.AVAGenService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class Utils {

    /**
     * Print the information of key features of a node
     * @param node the AccessibilityNodeInfo
     */
    public static void printNode(AccessibilityNodeInfo node) {

        if (node == null) {
            Log.d(AVAGenService.TAG, "node is null");
            return;
        }

        Log.d(AVAGenService.TAG, "printNodeInfo: " + getNodeDetails(node));
    }

    /**
     * Logs details of a node.
     */
    public static String getNodeDetails(AccessibilityNodeInfo node) {
        if (node == null) return "null";
        return "{ | packageName: " + node.getPackageName() +
                " | class name: " + node.getClassName() +
                " | text: " + node.getText() +
                " | content description: " + node.getContentDescription() +
                " | id : " + node.getViewIdResourceName() +
                " | checked : " + node.isChecked() +
                " } ";
    }


    /**
     * Given the root accessibilitynodeinfo, parse all of its valid sub-elements through using
     * a deque. See checkValidNode for what constitutes a valid sub-element in the UI tree.
     * @param root the root GUI element
     * @return an arraylist of GUI elements of the data type AccessibilityNodeInfo
     */
    public static ArrayList<AccessibilityNodeInfo> retrieveAllChildrenFromNode(AccessibilityNodeInfo root) {

        ArrayList<AccessibilityNodeInfo> result = new ArrayList<>();
        Deque<AccessibilityNodeInfo> elementsToIterate = new ArrayDeque<>();
        elementsToIterate.push(root);

        while (!elementsToIterate.isEmpty()) {

            // 1. pop one node out
            AccessibilityNodeInfo currNode = elementsToIterate.pop();

            // If valid, add current node to return result
            if (checkIsValidNode(currNode))
                result.add(currNode);

            // 2. push all its children in
            for (int i = 0; i < currNode.getChildCount(); i++) {
                AccessibilityNodeInfo childNode = currNode.getChild(i);
                if (childNode != null)
                    elementsToIterate.push(childNode);
            }
        }
        return result;
    }


    /**
     * Check if a given node is a valid node, a valid node means it has text, id or
     * content description, or class name is EditText
     *
     * @param node the node as a AccessibilityNodeInfo
     * @return true if valid
     */
    public static boolean checkIsValidNode(AccessibilityNodeInfo node) {
        if (node.isVisibleToUser()) {
            return node.getText() != null && node.getText().length() > 0
                    || node.getViewIdResourceName() != null && !node.getViewIdResourceName().isEmpty()
                    || node.getContentDescription() != null && node.getContentDescription().length() > 0
                    || node.getClassName() != null && node.getClassName().toString().toLowerCase().contains("edittext");
        }
        return false;
    }
}
