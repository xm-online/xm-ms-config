package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.TYPES;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EntitySpecDefinitionResolver implements DefinitionResolveStrategy {

    @Override
    public Map<String, JsonNode> resolve(Map<String, Object> specs, Map<String, String> specRef) {
        final List<Map<String, Object>> types = (List<Map<String, Object>>) specs.get(TYPES);
        specRef.forEach((key, value) -> {
            String dataSpecJsonSchema = types.stream()
                .filter(t -> key.equals(t.get("key")))
                .map(m -> (String) m.get("dataSpec"))
                .findFirst()
                .orElse(null);
        });
        return null;
    }

    @Override
    public boolean support(String refPrefix) {
        return false;
    }
}
