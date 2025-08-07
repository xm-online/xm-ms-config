package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.icthh.xm.ms.configuration.service.generator.dto.SpecDataResolveDto;
import java.util.Map;

public interface SpecResolveStrategy {

    String getPrefix();

    Map<String, JsonNode> resolve(SpecDataResolveDto specDataResolveDto);
}
