package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;

import com.codahale.metrics.annotation.Timed;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.service.TokenKeyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(API_PREFIX)
public class TokenResource {

    private final TokenKeyService tokenKeyService;

    @GetMapping("/token_key")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    //TODO maybe need to add security annotation
    public ResponseEntity<String> getTokenKey() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(tokenKeyService.getKey());
    }

    @GetMapping("/jwk")
    @Timed
    @LoggingAspectConfig(resultDetails = false)
    public ResponseEntity<String> getJwk() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(tokenKeyService.getJwk());
    }
}
