package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.utils.RefFinder.REF;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.dto.GenerateSpecDto;
import com.icthh.xm.ms.configuration.service.factory.DefinitionResolverFactory;
import com.icthh.xm.ms.configuration.utils.DeepYamlMerger;
import com.icthh.xm.ms.configuration.utils.RefFinder;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratorDtoService {

    private static final String LEP_DIR_PATH = "lep";
    private static final String GENERATED_DIR_PATH = "generated";
    public static final String TYPES = "types";
    public static final String DEFINITIONS = "definitions";
    private static final String XM_DEFINITIONS = "xmDefinition";
    private static final String XM_ENTITY_DEFINITION = "xmEntityDefinition";
    private static final String XM_ENTITY_INHERITANCE_DEFINITION = "xmEntityInheritanceDefinition";
    private static final String XM_ENTITY_DATA_SPEC = "xmEntityDataSpec";
    public static final Set<String> DEFINITION_PREFIXES = Set.of(DEFINITIONS, XM_ENTITY_DEFINITION,
        XM_ENTITY_INHERITANCE_DEFINITION, XM_ENTITY_DATA_SPEC);


    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ConfigurationService configurationService;
    private final DefinitionResolverFactory definitionResolverFactory;

    public void generateDto(GenerateSpecDto generateSpecDto) {
        final List<String> specContent = configurationService.findTenantConfigurations(generateSpecDto.getSpecPathsAntPattern(), false).values().stream()
            .map(Configuration::getContent)
            .toList();

        try {
            final Map<String, Object> deepMergedSpecs = DeepYamlMerger.mergeYamlContents(specContent);
            Map<String, JsonNode> refResolves = new HashMap<>();
//            final Map<String, String> specByJsonPath = new HashMap<>();

            generateSpecDto.getJsonPaths().stream().map(GenerateSpecDto.SpecJsonPathDto::getJsonPath).forEach(jsonPath -> {
                String specJsonSchema = JsonPath.read(deepMergedSpecs, jsonPath);
                if (StringUtils.isNotBlank(specJsonSchema)) {
//                    specByJsonPath.put(jsonPath, specJsonSchema);
                    JsonNode jsonNode = readTree(specJsonSchema);
                    Map<String, String> specRefs = RefFinder.findAllRefs(jsonNode);

                    specRefs.forEach((key, value) -> {
                        String section = value.replaceAll("^#/([^/]+)/.*", "$1");
                        refResolves.putAll(definitionResolverFactory.getDefinitionResolver(section).resolve(deepMergedSpecs, specRefs));
                    });

                }
            });

            replaceRefsWithObject(deepMergedSpecs, refResolves);


        } catch (Exception e) {
            // todo throw exception???
            throw new RuntimeException(e);
        }

    }

    private JsonNode readTree(String jsonSchema) {
        try {
            return objectMapper.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing json", e);
        }
    }


    @SuppressWarnings("unchecked")
    private void replaceRefsWithObject(Map<String, Object> node, Map<String, JsonNode> refResolved) {
        if (node.containsKey(REF)) {
            Object refObj = node.get(REF);
            if (refObj instanceof String refString) {
                Map<String, Object> replacement = buildReplacementMap(refString, refResolved);
                if (replacement != null) {
                    node.put(REF, replacement);
                }
            }
        }

        node.forEach((key, value) -> {
            if (value instanceof Map<?, ?> childMap) {
                Map<String, Object> castedMap = (Map<String, Object>) childMap;
                replaceRefsWithObject(castedMap, refResolved);
            } else if (value instanceof List<?> listValue) {
                listValue.forEach(item -> {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> castedItemMap = (Map<String, Object>) itemMap;
                        replaceRefsWithObject(castedItemMap, refResolved);
                    }
                });
            }
        });
    }

    private Map<String, Object> buildReplacementMap(String refString, Map<String, JsonNode> refResolved) {
        String path = refString.substring(2);
        String[] sections = path.split("/");
        if (sections.length < 2) return null;

        String firstKey = sections[0];
        String secondKey = sections[1];

        JsonNode resolvedNode = refResolved.get(refString);
        if (resolvedNode == null) return null;

        Object resolvedObject = objectMapper.convertValue(resolvedNode, Map.class);

        Map<String, Object> innerMap = new LinkedHashMap<>();
        innerMap.put(secondKey, resolvedObject);

        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put(firstKey, innerMap);

        return replacement;
    }
}
