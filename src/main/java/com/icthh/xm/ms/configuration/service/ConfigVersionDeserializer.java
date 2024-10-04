package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigVersionMixIn;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class ConfigVersionDeserializer {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .addMixIn(ConfigVersion.class, ConfigVersionMixIn.class)
        .registerModule(new JavaTimeModule());

    @SneakyThrows
    public ConfigVersion from(String value) {
        if (value == null) {
            return ConfigVersion.UNDEFINED_VERSION;
        }
        try {
            return mapper.readValue(value, ConfigVersion.class);
        } catch (JsonProcessingException e) {
            log.warn("Error parse version: {}", value, e);

            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
                log.info("ConfigVersionDeserializer: remoteAddress: [{}], value: [{}]", request.getRemoteAddr(), value);
            }

            // when during migration old config server send update to new config server
            return new ConfigVersion(value);
        }
    }
}
