package com.icthh.xm.ms.configuration.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.config.domain.Configuration;
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

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.OLD_CONFIG_HASH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(API_PREFIX)
public class ConfigurationClientResource {

    private final UrlPathHelper urlHelper = new UrlPathHelper();

    private final ConfigurationAdminResource configurationAdminResource;
    private final ConfigurationService configurationService;
    private final TenantContextHolder tenantContextHolder;

    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PostMapping(value = PROFILE + "/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.CLIENT.CREATE')")
    public ResponseEntity<Void> createConfiguration(@RequestBody String content, HttpServletRequest request) {
        String path = extractPath(request);
        configurationService.updateConfiguration(new Configuration(path, content));
        return ResponseEntity.created(new URI("/api" + path)).build();
    }

    @LoggingAspectConfig(inputExcludeParams = {"content"})
    @PutMapping(value = PROFILE + "/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.CLIENT.UPDATE')")
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
    public ResponseEntity<String> getConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        return configurationAdminResource.getConfiguration(request.getParameterMap().containsKey("toJson"), path);
    }

    @GetMapping(value = PROFILE + "/webapp/settings-public.yml")
    @Timed
    @LoggingAspectConfig(inputDetails = false, resultDetails = false)
    public ResponseEntity<String> getWebAppConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        return configurationAdminResource.getConfiguration(request.getParameterMap().containsKey("toJson"), path);
    }

    @DeleteMapping(PROFILE + "/**")
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.CLIENT.DELETE')")
    public ResponseEntity<Void> deleteConfiguration(HttpServletRequest request) {
        configurationService.deleteConfiguration(extractPath(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = PROFILE + "/refresh/**", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.CLIENT.REFRESH')")
    public ResponseEntity<Void> refreshConfiguration(HttpServletRequest request) {
        String path = extractUrlPath(request).substring(REFRESH.length());
        if (isBlank(path)) {
            configurationService.refreshTenantConfigurations();
        } else {
            configurationService.refreshConfiguration(getAbsolutePath(path));
        }
        return ResponseEntity.ok().build();
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

