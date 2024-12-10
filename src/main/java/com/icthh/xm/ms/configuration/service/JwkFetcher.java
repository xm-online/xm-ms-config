package com.icthh.xm.ms.configuration.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.MalformedURLException;
import java.net.URL;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwkFetcher {

    @SneakyThrows
    public String fetchJwk(String jwksEndpointUri) {
        try {
            URL url = new URL(jwksEndpointUri);
            return IOUtils.toString(url.openStream(), UTF_8);
        } catch (MalformedURLException ex) {
            log.error("Invalid jwk URL", ex);
        }
        return "";
    }

}
