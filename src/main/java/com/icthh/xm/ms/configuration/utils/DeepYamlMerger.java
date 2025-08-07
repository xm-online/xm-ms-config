package com.icthh.xm.ms.configuration.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.*;

public class DeepYamlMerger {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMergeMaps(Map<String, Object> map1, Map<String, Object> map2) {
        Map<String, Object> merged = new LinkedHashMap<>(map1);

        for (Map.Entry<String, Object> entry : map2.entrySet()) {
            String key = entry.getKey();
            Object value2 = entry.getValue();
            Object value1 = merged.get(key);

            if (value1 instanceof Map && value2 instanceof Map) {
                Map<String, Object> mergedChild = deepMergeMaps((Map<String, Object>) value1, (Map<String, Object>) value2);
                merged.put(key, mergedChild);
            } else if (value1 instanceof List && value2 instanceof List) {
                List<Object> mergedList = new ArrayList<>((List<?>) value1);
                mergedList.addAll((List<?>) value2);
                merged.put(key, mergedList);
            } else if (value2 != null) {
                merged.put(key, value2);
            }
        }

        return merged;
    }

    public static Map<String, Object> mergeYamlContents(List<String> yamlContents) throws Exception {
        Map<String, Object> merged = new LinkedHashMap<>();

        for (String yaml : yamlContents) {
            if (yaml == null || yaml.trim().isEmpty()) continue;

            Map<String, Object> map = readValue(yaml);
            merged = deepMergeMaps(merged, map);
        }

        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readValue(String yaml) throws Exception {
        return (Map<String, Object>) yamlMapper.readValue(yaml, Map.class);
    }
}

