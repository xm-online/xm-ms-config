package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.TokenKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(API_PREFIX)
public class ConfigMapResource {

    private final ConfigService configService;

    @GetMapping("/config_map")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<Map<String, String>> getAllConfigurations() {
        return ResponseEntity.ok(configService.getConfig());
    }
}
