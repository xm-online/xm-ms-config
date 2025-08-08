package com.icthh.xm.ms.configuration.service.generator.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SpecDataResolveDto {

    private Map<String, Object> deepMergeSpec;
    private Set<String> specRef;
    private ObjectNode specJsonSchema;
}
