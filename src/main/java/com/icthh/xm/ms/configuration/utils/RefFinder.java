package com.icthh.xm.ms.configuration.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RefFinder {

    public static final String REF = "$ref";

    public static Map<String, String> findAllRefs(JsonNode node) {
        Map<String, String> refs = new HashMap<>();
        findRefsRecursive(node, refs);
        return refs;
    }

    private static void findRefsRecursive(JsonNode node, Map<String, String> refs) {
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                if (REF.equals(field.getKey()) && field.getValue().isTextual()) {
                    String refValue = field.getValue().asText();
                    String refKeyName = refValue.substring(refValue.lastIndexOf("/") + 1);
                    refs.put(refKeyName , refValue);
                }
                findRefsRecursive(field.getValue(), refs);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                findRefsRecursive(item, refs);
            }
        }
    }
}
