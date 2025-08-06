package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface DefinitionResolveStrategy {

    Map<String, JsonNode> resolve(Map<String, Object> spec, Map<String, String> specRef);

    boolean support(String refPrefix);
}
