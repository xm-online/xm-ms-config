package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigVersionMixIn;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

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
        if (StringUtils.isEmpty(value)) {
            return ConfigVersion.UNDEFINED_VERSION;
        }
        try {
            return mapper.readValue(value, ConfigVersion.class);
        } catch (JsonProcessingException e) {
            log.warn("Error parse version: {}", value, e);
            // when during migration old config server send update to new config server
            return new ConfigVersion(value);
        }
    }
}
