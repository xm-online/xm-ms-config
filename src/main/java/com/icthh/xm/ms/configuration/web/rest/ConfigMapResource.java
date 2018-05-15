package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(API_PREFIX)
public class ConfigMapResource {

    private final ConfigService configService;

    @GetMapping("/config_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, Configuration>> getAllConfigurations(
        @RequestParam(name = "commit", required = false) String commit) {
        return ResponseEntity.ok(configService.getConfigurationMap(commit));
    }
}
