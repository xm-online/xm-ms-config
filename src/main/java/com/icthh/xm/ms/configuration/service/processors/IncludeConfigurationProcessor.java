package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Slf4j
@Component
public class IncludeConfigurationProcessor implements TenantConfigurationProcessor {

    public static final int GAP_FOR_FUTURE = 10;
    private static final String INCLUDE_KEYWORD = "$include";

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // Dependency tracking: key is the included file path, value is set of files that depend on it
    private final Map<String, Set<String>> dependencyRegistry = new ConcurrentHashMap<>();

    @Override
    public boolean isSupported(Configuration configuration) {
        return isConfigFile(configuration.getPath());
    }

    @SneakyThrows
    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage,
                                                    Set<Configuration> configToReprocess,
                                                    Map<String, Set<Configuration>> externalConfigs) {

        String filePath = configuration.getPath();
        String originalContent = configuration.getContent();
        log.trace("Config before replace {}", originalContent);

        reprocessDependsFile(configuration, originalStorage, configToReprocess, filePath);

        String content = replaceIncludeByActualContent(filePath, originalContent, originalStorage);
        log.trace("Config after replace {}", content);
        if (!content.equals(originalContent)) {
            return singletonList(new Configuration(configuration.getPath(), content));
        } else {
            return emptyList();
        }
    }

    @Override
    public Integer getPriority() {
        return TenantConfigurationProcessor.super.getPriority() - GAP_FOR_FUTURE;
    }

    private void reprocessDependsFile(Configuration configuration, Map<String, Configuration> originalStorage,
                                      Set<Configuration> configToReprocess, String filePath) {
        // Check if any files that depend on this file need to be reprocessed
        Set<String> dependentFiles = dependencyRegistry.get(filePath);
        if (dependentFiles != null) {
            for (String dependentFile : dependentFiles) {
                Configuration dependentConfig = originalStorage.get(dependentFile);
                if (dependentConfig != null && !dependentConfig.equals(configuration)) {
                    configToReprocess.add(dependentConfig);
                    log.debug("Adding dependent file to reprocess: {}", dependentFile);
                }
            }
        }
    }

    private String replaceIncludeByActualContent(String filePath, String originalContent,
                                                  Map<String, Configuration> originalStorage) {
        // Check if file ends with json, yml, or yaml
        if (!isConfigFile(filePath)) {
            return originalContent;
        }

        // Fast check using string contains method
        if (!originalContent.contains(INCLUDE_KEYWORD)) {
            return originalContent;
        }

        try {
            // Try to parse the file
            ObjectMapper mapper = getMapperForFile(filePath);
            Object parsedContent = mapper.readValue(originalContent, Object.class);

            // Process includes recursively
            Object processedContent = processIncludes(parsedContent, filePath, originalStorage, new HashMap<>());

            // Convert back to string
            return mapper.writeValueAsString(processedContent);
        } catch (Exception e) {
            log.error("Failed to parse file {} for include processing: {}", filePath, e.getMessage(), e);
            return originalContent;
        }
    }

    private boolean isConfigFile(String filePath) {
        return filePath.endsWith(".json") || filePath.endsWith(".yml") || filePath.endsWith(".yaml");
    }

    private ObjectMapper getMapperForFile(String filePath) {
        if (filePath.endsWith(".json")) {
            return jsonMapper;
        } else {
            return yamlMapper;
        }
    }

    @SuppressWarnings("unchecked")
    private Object processIncludes(Object node, String currentFilePath,
                                   Map<String, Configuration> originalStorage,
                                   Map<String, Object> processedIncludes) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            Map<String, Object> result = new HashMap<>();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (INCLUDE_KEYWORD.equals(key) && value instanceof String includePathRaw) {
                    if (!processIncludeFile(currentFilePath, includePathRaw, originalStorage, processedIncludes, result)) {
                        result.put(INCLUDE_KEYWORD, includePathRaw);
                    }
                } else if (INCLUDE_KEYWORD.equals(key) && value instanceof List<?> includePathsRaw) {
                    // Handle array of include paths: $include: [path1, path2, path3]
                    processIncludeArray(includePathsRaw, currentFilePath, originalStorage, processedIncludes, result);
                } else {
                    // Recursively process nested structures
                    result.put(key, processIncludes(value, currentFilePath, originalStorage, processedIncludes));
                }
            }
            return result;
        } else if (node instanceof List) {
            return processListNode((List<Object>) node, currentFilePath, originalStorage, processedIncludes);
        } else {
            return node;
        }
    }

    private void trackDependency(String absolutePath, String currentFilePath) {
        dependencyRegistry.computeIfAbsent(absolutePath, k -> ConcurrentHashMap.newKeySet())
                         .add(currentFilePath);
        log.debug("Registered dependency: {} depends on {}", currentFilePath, absolutePath);
    }

    private boolean processIncludeFile(String currentFilePath, String includePathRaw,
                                        Map<String, Configuration> originalStorage,
                                        Map<String, Object> processedIncludes,
                                        Map<String, Object> result) {
        String absolutePath = resolveIncludePath(currentFilePath, includePathRaw);
        trackDependency(absolutePath, currentFilePath);

        if (processedIncludes.containsKey(absolutePath)) {
            mergeIncludedContent(processedIncludes.get(absolutePath), result);
            return true;
        }

        Optional<Object> processed = loadAndProcessIncludedFile(absolutePath, originalStorage, processedIncludes);
        if (processed.isEmpty()) {
            return false;
        }
        mergeIncludedContent(processed.get(), result);
        return true;
    }

    private void processIncludeArray(List<?> includePathsRaw, String currentFilePath,
                                     Map<String, Configuration> originalStorage,
                                     Map<String, Object> processedIncludes,
                                     Map<String, Object> result) {
        List<String> failedPaths = new ArrayList<>();

        for (Object pathObj : includePathsRaw) {
            if (!(pathObj instanceof String includePathRaw)) {
                log.warn("Invalid path in $include array (not a string): {}, skipping", pathObj);
                continue;
            }
            if (!processIncludeFile(currentFilePath, includePathRaw, originalStorage, processedIncludes, result)) {
                failedPaths.add(includePathRaw);
            }
        }

        if (!failedPaths.isEmpty()) {
            result.put(INCLUDE_KEYWORD, failedPaths.size() == 1 ? failedPaths.getFirst() : failedPaths);
        }
    }

    private Optional<Object> loadAndProcessIncludedFile(String absolutePath,
                                                        Map<String, Configuration> originalStorage,
                                                        Map<String, Object> processedIncludes) {
        Configuration includedConfig = originalStorage.get(absolutePath);
        if (includedConfig == null) {
            log.warn("Included file not found in originalStorage: {}", absolutePath);
            return Optional.empty();
        }

        try {
            ObjectMapper mapper = getMapperForFile(absolutePath);
            Object includedContent = mapper.readValue(includedConfig.getContent(), Object.class);
            processedIncludes.put(absolutePath, includedContent);
            Object processedInclude = processIncludes(includedContent, absolutePath, originalStorage, processedIncludes);
            return Optional.of(processedInclude);
        } catch (Exception e) {
            log.error("Failed to process included file {}: {}", absolutePath, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeIncludedContent(Object processedInclude, Map<String, Object> result) {
        if (processedInclude instanceof Map) {
            Map<String, Object> includedMap = (Map<String, Object>) processedInclude;
            result.putAll(includedMap);
        } else {
            result.put(INCLUDE_KEYWORD, processedInclude);
        }
    }

    private List<Object> processListNode(List<Object> list, String currentFilePath,
                                        Map<String, Configuration> originalStorage,
                                        Map<String, Object> processedIncludes) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            result.add(processIncludes(item, currentFilePath, originalStorage, processedIncludes));
        }
        return result;
    }

    private String resolveIncludePath(String currentFilePath, String includePath) {
        if (includePath.startsWith("/")) {
            Path normalizedPath = Paths.get(includePath).normalize();
            return normalizedPath.toString().replace("\\", "/");
        } else {
            // Relative path - resolve based on current file path
            Path currentPath = Paths.get(currentFilePath).getParent();
            if (currentPath == null) {
                return includePath;
            }
            Path resolvedPath = currentPath.resolve(includePath).normalize();
            return resolvedPath.toString().replace("\\", "/");
        }
    }

}
