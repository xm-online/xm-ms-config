package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.PRIVATE;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final ConfigService configService;

    @GetMapping("/config_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, Configuration>> getAllConfigurations(
        @RequestParam(name = "version", required = false) String version) {
        return ResponseEntity.ok(configService.getConfigurationMap(version));
    }

    @PostMapping("/config_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, Configuration>> getAllConfigurations(@RequestBody GetConfigRequest getConfigRequest) {
        return ResponseEntity.ok(configService.getConfigurationMap(getConfigRequest.getVersion(), getConfigRequest.getPaths()));
    }

    @Data
    private static class GetConfigRequest {
        private List<String> paths;
        private String version;
    }

}
