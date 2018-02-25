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
import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
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

    private final ConfigurationService configurationService;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper ymlmapper = new ObjectMapper(new YAMLFactory());

    @PostMapping(value =  CONFIG, consumes = MULTIPART_FORM_DATA_VALUE)
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'files': #files, 'tenant': #tenant}, 'CONFIG.ADMIN.CREATE.LIST')")
    public ResponseEntity<Void> createConfigurations(@RequestParam(value = "files") List<MultipartFile> files,
                                                     @PathVariable("tenant") String tenant) {
        configurationService.createConfigurations(files);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = CONFIG + TENANTS + "/{tenant}/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @SneakyThrows
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.ADMIN.CREATE')")
    public ResponseEntity<Void> createConfiguration(@RequestBody String content, HttpServletRequest request) {
        String path = extractPath(request);
        configurationService.createConfiguration(new Configuration(path, content));
        return ResponseEntity.created(new URI("/api" + path)).build();
    }

    @PutMapping(value = CONFIG + TENANTS + "/{tenant}/**", consumes = {TEXT_PLAIN_VALUE, APPLICATION_JSON_VALUE})
    @Timed
    @PreAuthorize("hasPermission({'content': #content, 'request': #request}, 'CONFIG.ADMIN.UPDATE')")
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

    @GetMapping(value = CONFIG + "/**")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    @PostAuthorize("hasPermission({'returnObject': returnObject.body}, 'CONFIG.ADMIN.GET_LIST.ITEM')")
    public ResponseEntity<String> getConfiguration(HttpServletRequest request) {
        String path = extractPath(request);
        return getConfiguration(request.getParameterMap().containsKey("toJson"), path);
    }

    protected ResponseEntity<String> getConfiguration(Boolean toJson, String path) {
        Optional<Configuration> maybeConfiguration = configurationService.findConfiguration(path);
        if (!maybeConfiguration.isPresent()) {
            throw new EntityNotFoundException("Not found configuration.");
        }
        String content = maybeConfiguration.get().getContent();

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
    public ResponseEntity<Void> deleteConfiguration(HttpServletRequest request) {
        configurationService.deleteConfiguration(extractPath(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = CONFIG + "/refresh", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Timed
    @PreAuthorize("hasPermission({'request': #request}, 'CONFIG.ADMIN.REFRESH')")
    public ResponseEntity<Void> refreshConfiguration(HttpServletRequest request,
                                                     @RequestParam(name = "path", required = false) String path) {
        if (isBlank(path)) {
            configurationService.refreshConfigurations();
        } else {
            configurationService.refreshConfigurations(path);
        }
        return ResponseEntity.ok().build();
    }

    protected String extractPath(HttpServletRequest request) {
        return urlHelper.getPathWithinApplication(request).substring(API_PREFIX.length());
    }

}
