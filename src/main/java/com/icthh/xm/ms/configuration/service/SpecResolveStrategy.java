package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icthh.xm.ms.configuration.service.generator.dto.SpecDataResolveDto;
import java.util.Map;

public interface SpecResolveStrategy {

    String getPrefix();

    Map<String, ObjectNode> resolve(SpecDataResolveDto specDataResolveDto);
}
