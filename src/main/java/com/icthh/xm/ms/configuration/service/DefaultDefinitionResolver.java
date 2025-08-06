package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.DEFINITIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DefaultDefinitionResolver implements DefinitionResolveStrategy {

    private final static ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public Map<String, JsonNode> resolve(Map<String, Object> spec, Map<String, String> specRef) {
        final Map<String, JsonNode> refResolveResult = new HashMap<>();

        List<Map<String, Object>> definitions = (List<Map<String, Object>>) spec.get(DEFINITIONS);

        specRef.forEach((key, value) -> {
            definitions.stream()
                .filter(def -> key.equals(def.get("key")))
                .map(def -> (String) def.get("value"))
                .forEach(jsonSchema -> refResolveResult.put(value, getJsonNode(jsonSchema)));
        });

        return refResolveResult;
    }

    @Override
    public boolean support(String refPrefix) {
        return false;
    }

    private static JsonNode getJsonNode(String jsonSchema) {
        try {
            return objectMapper.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing json", e);
        }
    }
}
