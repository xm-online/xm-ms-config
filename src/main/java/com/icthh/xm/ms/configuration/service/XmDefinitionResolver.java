package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.DEFINITIONS;
import static com.icthh.xm.ms.configuration.service.GeneratorDtoService.XM_DEFINITIONS;
import static com.icthh.xm.ms.configuration.utils.RefFinder.DEFINITION_REF;
import static com.icthh.xm.ms.configuration.utils.RefFinder.FILE_REF;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.service.generator.RefReplacementComponent;
import com.icthh.xm.ms.configuration.service.generator.dto.SpecDataResolveDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XmDefinitionResolver implements SpecResolveStrategy {

    public static final String TENANT_KEY_PREFIX = "{tenantKey}";
    public static final String REF_PATH_PREFIX = "{refPathFile}";
    public static final String CONFIG_PATH_PREFIX = "/config/tenants/" + TENANT_KEY_PREFIX + "/" + REF_PATH_PREFIX;
    public static final String SECTION_ONE = "$1";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ConfigurationService configurationService;
    private final TenantContextHolder tenantContextHolder;


    @Override
    public String getPrefix() {
        return XM_DEFINITIONS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ObjectNode> resolve(SpecDataResolveDto specDataResolveDto) {
        final Map<String, ObjectNode> refResolveResult = new HashMap<>();
        Map<String, Object> deepMergeSpec = specDataResolveDto.getDeepMergeSpec();
        final List<Map<String, Object>> definitions = (List<Map<String, Object>>) deepMergeSpec.get(DEFINITIONS);
        String tenantKey = tenantContextHolder.getTenantKey();


        specDataResolveDto.getSpecRef().forEach(value -> {
            String key = value.substring(value.lastIndexOf("/") + 1);

            Map<String, Object> matchingDef = definitions.stream()
                    .filter(def -> key.equals(def.get(KEY)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Can't resolve $ref: " + value + " — no definition found for key: " + key));

            String refJsonSchema = (String) matchingDef.get(VALUE);

            if (StringUtils.isBlank(refJsonSchema)) {
                String refPathFile = (String) matchingDef.get(FILE_REF);
                String fullRefPathConfigFile = CONFIG_PATH_PREFIX
                        .replace(TENANT_KEY_PREFIX, tenantKey)
                        .replace(REF_PATH_PREFIX, refPathFile);

                refJsonSchema = configurationService.findConfiguration(fullRefPathConfigFile)
                        .map(Configuration::getContent)
                        .orElseThrow(() -> new IllegalArgumentException("Can't find configuration: " + fullRefPathConfigFile));
            }

//            specDataResolveDto.getSpecJsonSchema().set(DEFINITION_REF, getJsonNode(refJsonSchema));
            ObjectNode resolvedRefSchema = getJsonNode(refJsonSchema);
            refResolveResult.put(value, resolvedRefSchema);
//            refReplacementComponent.replaceRefsOnJsonSchema(specDataResolveDto.getSpecJsonSchema(), refResolveResult);
        });

        return refResolveResult;
    }

    private ObjectNode getJsonNode(String jsonSchema) {
        try {
            return (ObjectNode) objectMapper.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing json", e);
        }
    }
}
