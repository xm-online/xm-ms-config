package com.icthh.xm.ms.configuration.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.jsonschema2pojo.DefaultGenerationConfig;
import org.jsonschema2pojo.GenerationConfig;
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

                    getGeneratedJavaClasses(resolvedJsonNodes.toPrettyString(), specJsonPathDto.getClassName());
                }
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private String getGeneratedJavaClasses(String specJsonSchema, String className) {
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

    private JsonNode getJsonNode(String jsonSchema) {
        try {
            return objectMapper.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing json", e);
        }
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
