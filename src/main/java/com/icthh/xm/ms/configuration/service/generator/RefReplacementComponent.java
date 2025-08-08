package com.icthh.xm.ms.configuration.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RefReplacementComponent {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectNode addResolvedRefsOnJsonSchema(ObjectNode node, Map<String, ObjectNode> refMap) {
        ObjectNode result = objectMapper.createObjectNode();

        JsonNode refNode = node.get("$ref");
        if (refNode == null || !refNode.isTextual()) {
            return node;
        }

        String refValue = refNode.asText();
        result.put("$ref", refValue);

        ObjectNode rawSchema = refMap.get(refValue);
        if (rawSchema == null) {
            return result;
        }

        ObjectNode resolvedSchema = rawSchema.deepCopy();
        ObjectNode definitions = objectMapper.createObjectNode();

        resolveNestedRefs(resolvedSchema, refMap, definitions);


        ObjectNode refStructure = buildNestedStructure(refValue, resolvedSchema);
        merge(result, refStructure);
        merge(result, resolvedSchema);
        merge(result, definitions);

        return result;
    }

    private ObjectNode buildNestedStructure(String refPath, ObjectNode schema) {
        String[] pathParts = refPath.startsWith("#/") ? refPath.substring(2).split("/") : refPath.split("/");
        ObjectNode current = schema;
        for (int i = pathParts.length - 1; i >= 0; i--) {
            ObjectNode parent = objectMapper.createObjectNode();
            parent.set(pathParts[i], current);
            current = parent;
        }
        return current;
    }

    private void resolveNestedRefs(ObjectNode schemaNode, Map<String, ObjectNode> refMap, ObjectNode definitions) {
        Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();

            if (value.isObject()) {
                ObjectNode valueObj = (ObjectNode) value;
                JsonNode refNode = valueObj.get("$ref");

                if (refNode != null && refNode.isTextual()) {
                    String refValue = refNode.asText();

                    if (!definitions.has(getRefName(refValue))) {
                        ObjectNode nestedSchema = refMap.get(refValue);
                        if (nestedSchema == null) continue;

                        ObjectNode nestedCopy = nestedSchema.deepCopy();
                        resolveNestedRefs(nestedCopy, refMap, definitions);

                        ObjectNode nestedStructure = buildNestedStructure(refValue, nestedCopy);
                        merge(definitions, nestedStructure);
                    }

                    continue;
                }

                resolveNestedRefs(valueObj, refMap, definitions);

            } else if (value.isArray()) {
                for (JsonNode element : value) {
                    if (element.isObject()) {
                        resolveNestedRefs((ObjectNode) element, refMap, definitions);
                    }
                }
            }
        }
    }

    private String getRefName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private void merge(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();

            if (target.has(fieldName)) {
                JsonNode existing = target.get(fieldName);
                if (existing.isObject() && value.isObject()) {
                    merge((ObjectNode) existing, (ObjectNode) value);
                }
            } else {
                target.set(fieldName, value);
            }
        });
    }


//    public void replaceRefsOnJsonSchema(ObjectNode jsonNode, Map<String, ObjectNode> refResolved) {
//        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
//
//        while (fields.hasNext()) {
//            Map.Entry<String, JsonNode> entry = fields.next();
//            JsonNode nodeValue = entry.getValue();
//
//            if (nodeValue.isObject()) {
//                ObjectNode childObj = getJsonNodes(jsonNode, refResolved, (ObjectNode) nodeValue, entry);
//                if (childObj == null) {
//                    continue;
//                }
//
//                replaceRefsOnJsonSchema(childObj, refResolved);
//            } else if (nodeValue.isTextual()) {
//                String refValue = nodeValue.asText();
//                if (refResolved.containsKey(refValue)) {
//                    JsonNode newSchemaNode = refResolved.get(refValue);
//                    jsonNode.set(entry.getKey(), newSchemaNode);
//                    replaceRefsOnJsonSchema(jsonNode, refResolved);
//                }
//            } else if (nodeValue.isArray()) {
//                for (int i = 0; i < nodeValue.size(); i++) {
//                    JsonNode item = nodeValue.get(i);
//                    if (item.isObject()) {
//                        replaceRefsOnJsonSchema((ObjectNode) item, refResolved);
//                    }
//                }
//            }
//        }
//    }
//
//    private ObjectNode getJsonNodes(ObjectNode node, Map<String, ObjectNode> refResolved, ObjectNode childObj, Map.Entry<String, JsonNode> entry) {
//        final AtomicBoolean isReplacement = new AtomicBoolean(false);
//        childObj.findParents(DEFINITION_REF).forEach(field -> {
//            Set<String> allRefs = RefFinder.findAllRefs(field);
//            allRefs.forEach(ref -> {
//                JsonNode replacement = refResolved.get(ref);
//
//                if (replacement != null) {
//                    node.set(entry.getKey(), replacement.deepCopy());
//                    isReplacement.set(true);
//                }
//            });
//        });
//
//        return isReplacement.get() ? null : childObj;
//    }
}
