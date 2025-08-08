package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.XM_ENTITY_DEFINITION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icthh.xm.ms.configuration.service.generator.dto.SpecDataResolveDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XmEntityDefinitionResolver implements SpecResolveStrategy {

    private final XmDefinitionResolver xmDefinitionResolver;

    @Override
    public String getPrefix() {
        return XM_ENTITY_DEFINITION;
    }

    @Override
    public Map<String, ObjectNode> resolve(SpecDataResolveDto specDataResolveDto) {
        return xmDefinitionResolver.resolve(specDataResolveDto);
    }
}
