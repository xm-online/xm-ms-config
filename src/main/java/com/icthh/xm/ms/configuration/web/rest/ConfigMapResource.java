package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.PRIVATE;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.OLD_CONFIG_HASH;
import static org.springframework.http.HttpStatus.CONFLICT;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.utils.ConfigPathUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(API_PREFIX + PRIVATE)
public class ConfigMapResource {

    private final ConfigurationService configurationService;

    @GetMapping("/config_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, Configuration>> getAllConfigurations(
        @RequestParam(name = "version", required = false) String version) {
        return ResponseEntity.ok(configurationService.getConfigurationMap(version));
    }

    @PostMapping("/config_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, Configuration>> getAllConfigurations(@RequestBody GetConfigRequest getConfigRequest) {
        return ResponseEntity.ok(configurationService.getConfigurationMap(getConfigRequest.getVersion(), getConfigRequest.getPaths()));
    }

    @PutMapping(value = "/config")
    @Timed
    public ResponseEntity<Void> updateConfiguration(@RequestBody Configuration configuration,
                                                    @RequestParam(name = OLD_CONFIG_HASH, required = false) String oldConfigHash) {
        try {
            configurationService.updateConfiguration(configuration, oldConfigHash);
        } catch (ConcurrentConfigModificationException e) {
            log.warn("Error update configuration", e);
            return ResponseEntity.status(CONFLICT).build();
        }
        return ResponseEntity.ok().build();
    }

    // api required tenant context
    @PutMapping(value = "/profile/configs_update")
    @Timed
    public ResponseEntity<Void> updateConfiguration(@RequestBody List<Configuration> configs) {
        try {
            configurationService.updateConfigurationsFromList(configs);
        } catch (ConcurrentConfigModificationException e) {
            log.warn("Error update configuration", e);
            return ResponseEntity.status(CONFLICT).build();
        }
        return ResponseEntity.ok().build();
    }

    @Data
    private static class GetConfigRequest {
        private List<String> paths;
        private String version;

        @Override
        public String toString() {
            return "GetConfigRequest{" +
                   "version = " + version +
                   ", paths.length = " + (paths != null ? paths.size() : null) +
                   ", top paths = " + ConfigPathUtils.printPathsWithLimit(paths) +
                   "}";
        }
    }

}

