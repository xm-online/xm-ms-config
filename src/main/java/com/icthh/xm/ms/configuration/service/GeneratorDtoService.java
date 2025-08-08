package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.utils.RefFinder.DEFINITION_REF;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.service.generator.JsonSchemaToJavaGenerator;
import com.icthh.xm.ms.configuration.service.generator.RefReplacementComponent;
import com.icthh.xm.ms.configuration.service.generator.dto.GenerateSpecDto;
import com.icthh.xm.ms.configuration.service.factory.DefinitionResolverFactory;
import com.icthh.xm.ms.configuration.service.generator.dto.SpecDataResolveDto;
import com.icthh.xm.ms.configuration.utils.DeepYamlMerger;
import com.icthh.xm.ms.configuration.utils.RefFinder;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.jsonschema2pojo.DefaultGenerationConfig;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.SourceType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratorDtoService {

    private static final String LEP_DIR_PATH = "lep";
    private static final String GENERATED_DIR_PATH = "generated";
    public static final String TYPES = "types";
    public static final String DEFINITIONS = "definitions";
    public static final String XM_DEFINITIONS = "xmDefinition";
    public static final String XM_ENTITY_DEFINITION = "xmEntityDefinition";
    public static final String XM_ENTITY_INHERITANCE_DEFINITION = "xmEntityInheritanceDefinition";
    public static final String XM_ENTITY_DATA_SPEC = "xmEntityDataSpec";
    public static final Set<String> DEFINITION_PREFIXES = Set.of(DEFINITIONS, XM_ENTITY_DEFINITION,
        XM_ENTITY_INHERITANCE_DEFINITION, XM_ENTITY_DATA_SPEC);

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ConfigurationService configurationService;
    private final DefinitionResolverFactory definitionResolverFactory;
    private final JsonSchemaToJavaGenerator jsonSchemaToJavaGenerator;
    private final RefReplacementComponent refReplacementComponent;

    public void generateDto(GenerateSpecDto generateSpecDto) {
        final List<String> specContent = configurationService.findTenantConfigurations(generateSpecDto.getSpecPathsAntPattern(), false).values().stream()
            .map(Configuration::getContent)
            .toList();

        try {
            final Map<String, Object> deepMergedSpecs = DeepYamlMerger.mergeYamlContents(specContent);
            Map<String, ObjectNode> refResolves = new HashMap<>();

            generateSpecDto.getJsonPaths().forEach(specJsonPathDto -> {
                String jsonPath = specJsonPathDto.getJsonPath();
                JSONArray matches = JsonPath.read(deepMergedSpecs, jsonPath);
                String specJsonSchema = (String) matches.get(0);
                if (specJsonSchema != null) {
                    ObjectNode specJsonObject = (ObjectNode) getJsonNode(specJsonSchema);
                    resolvingRef(specJsonObject, deepMergedSpecs, refResolves);
                    ObjectNode resolvedJsonNodes = refReplacementComponent.addResolvedRefsOnJsonSchema(specJsonObject, refResolves);

//                    ObjectNode jsonNodeObject = (ObjectNode) jsonNode;
//                    replaceRefsOnJsonSchema(jsonNodeObject, refResolves);

//                    String json = getJson(jsonNodeObject);
                    getGeneratedJavaClass(resolvedJsonNodes.toPrettyString(), specJsonPathDto.getClassName());
                }
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private String getGeneratedJavaClass(String specJsonSchema, String className) {
        String javaClassContent = jsonSchemaToJavaGenerator.convertSchemaToJava(getGeneratorConfig(), specJsonSchema, className, "com.icthh.xm.ms.configuration.generated");
        log.info("Java class content: {}", javaClassContent);
        return javaClassContent;
    }

    private void resolvingRef(ObjectNode jsonSchemaNode, Map<String, Object> deepMergedSpecs, Map<String, ObjectNode> resolvedRefs) {
        Set<String> specRefs = RefFinder.findAllRefs(jsonSchemaNode);

        specRefs.forEach(refValue -> {
            if (resolvedRefs.containsKey(refValue)) {
                return;
            }
            String section = refValue.replaceAll("^#/([^/]+)/.*", "$1");
            Map<String, ObjectNode> resolvedDefinition = definitionResolverFactory.getDefinitionResolver(section)
                    .resolve(SpecDataResolveDto.builder()
                            .deepMergeSpec(deepMergedSpecs)
                            .specRef(Set.of(refValue)) //probably group by to section and resolve it by resolver..
                            .specJsonSchema(jsonSchemaNode)
                            .build()
                    );
            resolvedRefs.putAll(resolvedDefinition);
            resolvedDefinition.values().forEach(node -> resolvingRef(node, deepMergedSpecs, resolvedRefs));
        });
    }

    private JsonNode ensureJsonNode(JsonNode node) {
        if (node.isTextual()) {
            try {
                return objectMapper.readTree(node.asText());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to parse JSON from text node: " + node.asText(), e);
            }
        }
        return node;
    }

    private JsonNode getJsonNode(String jsonSchema) {
        try {
            return objectMapper.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing json", e);
        }
    }

    private String getJson(ObjectNode jsonSchema) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonSchema.asText());
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(jsonNode);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing json", e);
        }
    }


//    @SuppressWarnings("unchecked")
//    private Map<String, Object> replaceRefsWithObject(ObjectNode objectNode, Map<String, JsonNode> refResolved) {
//
//        jsonSchemaSpecs.values().forEach(jsonSchema -> {
//            Set<String> allRefsSchema = RefFinder.findAllRefs(getJsonNode((String) jsonSchema));
//            allRefsSchema.forEach(ref -> {
//                String section = ref.replaceAll("^#/([^/]+)/.*", "$1");
//                if (DEFINITION_PREFIXES.contains(section)) {
//                    addReplacementForRef(ref, copyJsonSchemaSpecs, refResolved);
//                }
//            });
//        });
//
//        jsonSchemaSpecs.forEach((key, value) -> {
//            if (value instanceof Map<?, ?> childMap) {
//                Map<String, Object> castedMap = (Map<String, Object>) childMap;
//                replaceRefsWithObject(castedMap, refResolved);
//            } else if (value instanceof List<?> listValue) {
//                listValue.forEach(item -> {
//                    if (item instanceof Map<?, ?> itemMap) {
//                        Map<String, Object> castedItemMap = (Map<String, Object>) itemMap;
//                        replaceRefsWithObject(castedItemMap, refResolved);
//                    }
//                });
//            }
//        });
//
//        return copyJsonSchemaSpecs;
//    }

    private void addReplacementForRef(String refKey, Map<String, Object> jsonSchemaSpecs, Map<String, JsonNode> refResolved) {
        Map<String, Object> replacement = buildReplacementMap(refKey, refResolved);
        if (replacement != null) {
            jsonSchemaSpecs.put(refKey, replacement);
        }
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

    private GenerationConfig getGeneratorConfig() {
        return new DefaultGenerationConfig() {
            @Override
            public char[] getPropertyWordDelimiters() {
                return new char[] { ' ', '-', '_'};
            }
            @Override
            public boolean isIncludeAdditionalProperties() {
                return false;
            }
        };
    }
}
