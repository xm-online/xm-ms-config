package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigVersionMixIn;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class ConfigVersionDeserializer {
    private final ObjectMapper mapper = JsonMapper.builder()
        .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
            )
        .addMixIn(ConfigVersion.class, ConfigVersionMixIn.class)
        .build();

    @SneakyThrows
    public ConfigVersion from(String value) {
        if (StringUtils.isEmpty(value)) {
            return ConfigVersion.UNDEFINED_VERSION;
        }
        try {
            return mapper.readValue(value, ConfigVersion.class);
        } catch (JacksonException e) {
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
