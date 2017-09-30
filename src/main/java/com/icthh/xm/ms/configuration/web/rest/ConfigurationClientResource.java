package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.ms.configuration.config.tenant.TenantContext;
import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(API_PREFIX)
public class ConfigurationClientResource {

    private final UrlPathHelper urlHelper = new UrlPathHelper();

    private final ConfigurationAdminResource configurationAdminResource;
    private final ConfigurationService configurationService;

    @PostMapping(value = PROFILE + "/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @SneakyThrows
    public ResponseEntity<Void> createConfiguration(@RequestBody String content, HttpServletRequest request) {
        String path = extractPath(request);
        configurationService.createConfiguration(new Configuration(path, content));
        return ResponseEntity.created(new URI("/api" + path)).build();
    }

    @PutMapping(value = PROFILE + "/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    public ResponseEntity<Void> updateConfiguration(@RequestBody String content, HttpServletRequest request) {
        configurationService.updateConfiguration(new Configuration(extractPath(request), content));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = PROFILE + "/**")
    @Timed
    public ResponseEntity<String> getConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        return configurationAdminResource.getConfiguration(request.getParameterMap().containsKey("toJson"), path);
    }

    @DeleteMapping(PROFILE + "/**")
    @Timed
    public ResponseEntity<Void> deleteConfiguration(HttpServletRequest request) {
        configurationService.deleteConfiguration(extractPath(request));
        return ResponseEntity.ok().build();
    }

    private String extractPath(HttpServletRequest request) {
        String relativePath = urlHelper.getPathWithinApplication(request).substring(API_PREFIX.length() + PROFILE.length());
        return CONFIG + TENANTS + "/" + TenantContext.getCurrent().getTenant() + relativePath;
    }

}

