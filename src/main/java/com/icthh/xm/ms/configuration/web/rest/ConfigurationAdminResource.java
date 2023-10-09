package com.icthh.xm.ms.configuration.web.rest;

import static com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance;
import static com.icthh.xm.ms.configuration.config.Constants.*;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.OLD_CONFIG_HASH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.*;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.commons.permission.annotation.PrivilegeDescription;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationsHashSumDto;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(API_PREFIX)
public class ConfigurationAdminResource {

    private final UrlPathHelper urlHelper = new UrlPathHelper();
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper ymlmapper = new ObjectMapper(new YAMLFactory());

    private final ConfigurationService configurationService;

    @PostMapping(value = CONFIG, consumes = MULTIPART_FORM_DATA_VALUE)
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'files': #files, 'tenant': #tenant}, 'CONFIG.ADMIN.CREATE.LIST')")
    @PrivilegeDescription("Privilege to create list of configurations for admin")
    public ResponseEntity<Void> createConfigurations(@RequestParam(value = "files") List<MultipartFile> files) {
        configurationService.createConfigurations(files);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = INMEMORY + CONFIG, consumes = MULTIPART_FORM_DATA_VALUE)
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'files': #files, 'tenant': #tenant}, 'CONFIG.ADMIN.UPDATE_IN_MEMORY.LIST')")
    @PrivilegeDescription("Privilege to update list of configurations in memory for admin")
    public ResponseEntity<Void> updateConfigurationsInMemory(@RequestParam(value = "files") List<MultipartFile> files) {
        configurationService.updateConfigurationsInMemory(files);
        return ResponseEntity.ok().build();
    }

    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PostMapping(value = CONFIG + TENANTS + "/{tenant}/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.ADMIN.CREATE')")
    @PrivilegeDescription("Privilege to create configuration for admin")
    public ResponseEntity<Void> createConfiguration(@RequestBody String content, HttpServletRequest request) {
        String path = extractPath(request);
        configurationService.updateConfiguration(new Configuration(path, content));
        return ResponseEntity.created(new URI("/api" + path)).build();
    }

    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PutMapping(value = CONFIG + TENANTS + "/{tenant}/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.ADMIN.UPDATE')")
    @PrivilegeDescription("Privilege to update configuration for admin")
    public ResponseEntity<Void> updateConfiguration(@RequestBody String content,
                                                    HttpServletRequest request,
                                                    @RequestParam(name = OLD_CONFIG_HASH, required = false) String oldConfigHash) {
        Configuration configuration = new Configuration(extractPath(request), content);
        try {
            configurationService.updateConfiguration(configuration, oldConfigHash);
        } catch (ConcurrentConfigModificationException e) {
            log.warn("Error update configuration", e);
            return ResponseEntity.status(CONFLICT).build();
        }
        return ResponseEntity.ok().build();
    }


    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PutMapping(value = INMEMORY + CONFIG + TENANTS + "/{tenant}/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.ADMIN.UPDATE_IN_MEMORY')")
    @PrivilegeDescription("Privilege to update configuration in memory for admin")
    public ResponseEntity<Void> updateConfigurationInMemory(@RequestBody(required = false) String content,
                                                            HttpServletRequest request) {
        String path = extractPath(request).substring(INMEMORY.length());
        Configuration configuration = new Configuration(path, content);
        configurationService.updateConfigurationInMemory(configuration);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = INMEMORY + CONFIG + TENANTS + "/{tenant}", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.ADMIN.DELETE_IN_MEMORY')")
    @PrivilegeDescription("Privilege to delete configuration in memory for admin")
    public ResponseEntity<Void> deleteConfigurationInMemory(@RequestBody(required = false) List<String> paths) {
        configurationService.deleteConfigurationInMemory(paths);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = CONFIG + "/**")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    @PostAuthorize("hasPermission({'returnObject': returnObject.body, 'request': #request}, 'CONFIG.ADMIN.GET_LIST.ITEM')")
    @PrivilegeDescription("Privilege to get configuration for admin")
    public ResponseEntity<String> getConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        String version = request.getParameter("version");
        Optional<Configuration> configuration = configurationService.findConfiguration(path, version);
        return toResponse(request.getParameterMap().containsKey("toJson"), path, configuration);
    }

    @GetMapping(value = "/version")
    @Timed
    @PostAuthorize("hasPermission({'returnObject': returnObject}, 'CONFIG.ADMIN.GET_VERSION')")
    @PrivilegeDescription("Privilege to get version for admin")
    public ResponseEntity<String> getVersion() {
        return ResponseEntity.ok(configurationService.getVersion());
    }

    protected ResponseEntity<String> getConfiguration(Boolean toJson, String path) {
        return getConfiguration(toJson, false, path);
    }

    protected ResponseEntity<String> getConfiguration(Boolean toJson, Boolean processed, String path) {
        return toResponse(toJson, path, configurationService.findProcessedConfiguration(path, processed));
    }

    protected ResponseEntity<String> toResponse(Boolean toJson, String path, Optional<Configuration> maybeConfiguration) {
        Configuration configuration = maybeConfiguration.orElseThrow(
            () -> new EntityNotFoundException("Not found configuration.")
        );
        return createResponse(toJson, path, configuration);
    }

    protected ResponseEntity<String> createResponse(Boolean toJson, String path, Configuration maybeConfiguration) {
        String content = maybeConfiguration.getContent();

        if (path.endsWith(".yml") && toJson) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(convertToJson(content));
        } else if (path.endsWith(".json")) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(content);
        } else {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(content);
        }
    }

    @SneakyThrows
    private String convertToJson(String yml) {
        MapType type = defaultInstance().constructMapType(HashMap.class, String.class, Object.class);
        Map<String, Object> properties = this.ymlmapper.readValue(yml, type);
        return this.jsonMapper.writeValueAsString(properties);
    }

    @DeleteMapping(CONFIG + TENANTS + "/{tenant}/**")
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.ADMIN.DELETE')")
    @PrivilegeDescription("Privilege to delete configuration for admin")
    public ResponseEntity<Void> deleteConfiguration(HttpServletRequest request) {
        configurationService.deleteConfiguration(extractPath(request));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(CONFIG + TENANTS + "/{tenant}")
    @Timed
    @PreAuthorize("hasPermission({'request': #paths}, 'CONFIG.ADMIN.DELETE.LIST')")
    @PrivilegeDescription("Privilege to delete list of configurations for admin")
    public ResponseEntity<Void> deleteConfigurations(@RequestBody(required = false) List<String> paths,
                                                     HttpServletRequest request) {
        List<String> nonNullPaths = Optional.ofNullable(paths)
                                           .orElseGet(Collections::emptyList);

        if (nonNullPaths.isEmpty()) {
            configurationService.deleteConfiguration(extractPath(request));
        } else {
            configurationService.deleteConfigurations(nonNullPaths);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = CONFIG + REFRESH + "/**", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.ADMIN.REFRESH')")
    @PrivilegeDescription("Privilege to refresh configuration for admin")
    public ResponseEntity<Void> refreshConfiguration(HttpServletRequest request) {
        configurationService.assertAdminRefreshAvailable();
        String path = extractPath(request).substring(CONFIG.length() + REFRESH.length());
        if (isBlank(path)) {
            configurationService.refreshConfiguration();
        } else {
            configurationService.refreshConfiguration(path);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = CONFIG + REFRESH + "/available")
    @Timed
    @PreAuthorize("hasPermission({}, 'CONFIG.ADMIN.REFRESH')")
    @PrivilegeDescription("Privilege to check admin refresh availability")
    public ResponseEntity<Map<String, Boolean>> isAdminRefreshAvailable() {
        return ResponseEntity.ok().body(Map.of("available", configurationService.isAdminRefreshAvailable()));
    }

    @PostMapping(value = CONFIG + RECLONE, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.ADMIN.RECLONE')")
    @PrivilegeDescription("Privilege to reclone configuration")
    public ResponseEntity<Void> recloneConfiguration(HttpServletRequest request) {
        configurationService.recloneConfiguration();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = CONFIG + "/zip", consumes = MULTIPART_FORM_DATA_VALUE)
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'files': #files, 'tenant': #tenant}, 'CONFIG.ADMIN.UPDATE_BY_ZIP')")
    @PrivilegeDescription("Privilege to create list of configurations for admin")
    public ResponseEntity<Void> updateByZipFile(@RequestParam(value = "file") MultipartFile file) {
        configurationService.updateConfigurationsFromZip(file);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = CONFIG + TENANTS + "/{tenant}" + "/hash")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    @PostAuthorize("hasPermission({'returnObject': returnObject.body, 'request': #request}, 'CONFIG.ADMIN.GET_HASH_SUM')")
    @PrivilegeDescription("Privilege to get configuration hash sum for admin")
    public ResponseEntity<ConfigurationsHashSumDto> getConfigurationsHashSum(@PathVariable String tenant) {
        return ResponseEntity.ok(configurationService.findConfigurationsHashSum(tenant));
    }

    protected String extractPath(HttpServletRequest request) {
        return urlHelper.getPathWithinApplication(request).substring(API_PREFIX.length());
    }

}
