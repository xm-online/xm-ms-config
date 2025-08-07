package com.icthh.xm.ms.configuration.utils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class RefFinder {

    public static final String DEFINITION_REF = "$ref";
    public static final String FILE_REF = "ref";

    public static Set<String> findAllRefs(JsonNode node) {
        Set<String> refs = new HashSet<>();
        findRefsRecursive(node, refs);
        return refs;
    }

    private static void findRefsRecursive(JsonNode node, Set<String> refs) {
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                if ((DEFINITION_REF.equals(field.getKey()) && field.getValue().isTextual())) {
                    String refValue = field.getValue().asText();
                    refs.add(refValue);
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
