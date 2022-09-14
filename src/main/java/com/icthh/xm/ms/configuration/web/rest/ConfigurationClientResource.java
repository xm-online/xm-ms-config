package com.icthh.xm.ms.configuration.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.permission.annotation.PrivilegeDescription;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.OLD_CONFIG_HASH;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getBooleanParameter;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(API_PREFIX)
public class ConfigurationClientResource {

    private static final String EMPTY_YML = "---";
    private final UrlPathHelper urlHelper = new UrlPathHelper();

    private final ConfigurationAdminResource configurationAdminResource;
    private final ConfigurationService configurationService;
    private final TenantContextHolder tenantContextHolder;

    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PostMapping(value = PROFILE + "/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.CLIENT.CREATE')")
    @PrivilegeDescription("Privilege to create config for client")
    public ResponseEntity<Void> createConfiguration(@RequestBody String content, HttpServletRequest request) {
        String path = extractPath(request);
        configurationService.updateConfiguration(new Configuration(path, content));
        return ResponseEntity.created(new URI("/api" + path)).build();
    }

    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PutMapping(value = PROFILE + "/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.CLIENT.UPDATE')")
    @PrivilegeDescription("Privilege to update config for client")
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

    @GetMapping(value = PROFILE + "/**")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    @PostAuthorize("hasPermission({'returnObject': returnObject.body}, 'CONFIG.CLIENT.GET_LIST.ITEM')")
    @PrivilegeDescription("Privilege to get config for client")
    public ResponseEntity<String> getConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        return configurationAdminResource.getConfiguration(request.getParameterMap().containsKey("toJson"), path);
    }

    @GetMapping(value = PROFILE + "/webapp/settings-private.yml")
    @Timed
    @PostAuthorize("hasPermission({'returnObject': returnObject.body}, 'CONFIG.CLIENT.WEBAPP.GET_LIST.ITEM')")
    @LoggingAspectConfig(inputDetails = false, resultDetails = false)
    @PrivilegeDescription("Privilege to get private ui settings")
    public ResponseEntity<String> getWebAppPrivateConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        Boolean processed = request.getParameterMap().containsKey("processed");
        Configuration maybeConfiguration = configurationService.findProcessedConfiguration(path, processed)
                                                               .orElse(new Configuration(path, EMPTY_YML));
        Boolean toJson = request.getParameterMap().containsKey("toJson");
        return configurationAdminResource.createResponse(toJson, path, maybeConfiguration);
    }

    @GetMapping(value = PROFILE + "/webapp/settings-public.yml")
    @Timed
    @LoggingAspectConfig(inputDetails = false, resultDetails = false)
    public ResponseEntity<String> getWebAppConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        boolean toJson = request.getParameterMap().containsKey("toJson");
        boolean processed = getBooleanParameter(request, "processed");
        return configurationAdminResource.getConfiguration(toJson, processed, path);
    }

    @GetMapping(value = PROFILE + "/webapp/public/**")
    @Timed
    @LoggingAspectConfig(inputDetails = false, resultDetails = false)
    public ResponseEntity<String> getPublicWebAppConfigurations(HttpServletRequest request) {
        String path = extractPath(request);
        boolean toJson = request.getParameterMap().containsKey("toJson");
        boolean processed = getBooleanParameter(request, "processed");
        return configurationAdminResource.getConfiguration(toJson, processed, path);
    }

    @DeleteMapping(PROFILE + "/**")
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.CLIENT.DELETE')")
    @PrivilegeDescription("Privilege to delete config for client")
    public ResponseEntity<Void> deleteConfiguration(HttpServletRequest request) {
        configurationService.deleteConfiguration(extractPath(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = PROFILE + "/refresh/**", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.CLIENT.REFRESH')")
    @PrivilegeDescription("Privilege to refresh config for client")
    public ResponseEntity<Void> refreshConfiguration(HttpServletRequest request) {
        String path = extractUrlPath(request).substring(REFRESH.length());
        if (isBlank(path)) {
            configurationService.refreshTenantConfigurations();
        } else {
            configurationService.refreshConfiguration(getAbsolutePath(path));
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = PROFILE + "/configs_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, Configuration>> getConfigurationsByPaths(@RequestBody List<String> paths) {
        List<String> nonNullPaths = Optional.ofNullable(paths)
            .orElseGet(Collections::emptyList);

        Map<String, Configuration> configurations = configurationService.findConfigurations(nonNullPaths);
        return ResponseEntity.ok(configurations);
    }

    private String extractPath(HttpServletRequest request) {
        String relativePath = extractUrlPath(request);
        return getAbsolutePath(relativePath);
    }

    private String extractUrlPath(HttpServletRequest request) {
        return urlHelper.getPathWithinApplication(request).substring(API_PREFIX.length() + PROFILE.length());
    }


    private String getAbsolutePath(String relativePath) {
        return getTenantPathPrefix(tenantContextHolder) + relativePath;
    }

}

