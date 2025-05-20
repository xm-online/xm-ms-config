package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.security.oauth2.JwtVerificationKeyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class MsConfigJwtVerificationKeyClient implements JwtVerificationKeyClient {

    private final TokenKeyService tokenKeyService;

    @Override
    public byte[] fetchKeyContent() {
        String content = tokenKeyService.getKey();
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
