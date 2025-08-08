package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.DEFINITIONS;
import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.XM_ENTITY_DATA_SPEC;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icthh.xm.ms.configuration.service.generator.dto.SpecDataResolveDto;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class XmEntityDataSpecResolver implements SpecResolveStrategy {

    private static final String DATA_SPEC_KEY = "dataSpec";

    @Override
    public String getPrefix() {
        return XM_ENTITY_DATA_SPEC;
    }

    @Override
    public Map<String, ObjectNode> resolve(SpecDataResolveDto specDataResolveDto) {
//        String specJsonSchema = specDataResolveDto.getSpecJsonSchema();
        Map<String, Object> deepMergeSpec = specDataResolveDto.getDeepMergeSpec();

        final List<Map<String, Object>> types = (List<Map<String, Object>>) deepMergeSpec.get(DEFINITIONS);

        specDataResolveDto.getSpecRef().forEach(value -> {
            String specKey = value.substring(value.lastIndexOf("/") + 1);
            String dataSpecJsonSchema = types.stream()
                .filter(t -> specKey.equals(t.get("key")))
                .map(m -> (String) m.get(DATA_SPEC_KEY))
                .findFirst()
                .orElse(null);
        });
        return null;
    }
}
