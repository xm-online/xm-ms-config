package com.icthh.xm.ms.configuration.service;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.icthh.xm.ms.configuration.repository.kafka.SystemQueueConsumer.JWK_UPDATE;
import static java.lang.System.currentTimeMillis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import com.icthh.xm.commons.messaging.event.system.SystemEvent;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpdateJwkEventService {

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(new JavaTimeModule());

    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();

    private final KafkaTemplate<String, String> template;
    private final ApplicationProperties applicationProperties;
    private final JwksService jwksService;
    private final ConfigurationService configurationService;
    private final String applicationName;

    public UpdateJwkEventService(KafkaTemplate<String, String> template,
                                 ApplicationProperties applicationProperties,
                                 JwksService jwksService,
                                 ConfigurationService configurationService,
                                 @Value("${spring.application.name}")
                                 String applicationName) {
        this.template = template;
        this.applicationProperties = applicationProperties;
        this.jwksService = jwksService;
        this.configurationService = configurationService;
        this.applicationName = applicationName;
    }

    @SneakyThrows
    public void consumerEvent(SystemEvent systemEvent) {
        UpdateClientsEvent event = mapper.convertValue(systemEvent.getDataMap(), UpdateClientsEvent.class);
        Integer debounceSeconds = applicationProperties.getJwkUpdateDebounceSeconds();
        if (debounceSeconds != null) {
            Long lastUpdateTime = this.lastUpdateTime.getOrDefault(systemEvent.getTenantKey(), 0L);
            if ((event.getUpdateTimestamp() - lastUpdateTime) < debounceSeconds * 1000) {
                log.info("Skip jwk update, updateTimestamp: {} - lastUpdateTime: {} < jwkUpdateDebounceSeconds: {}",
                    event.getUpdateTimestamp(), lastUpdateTime, debounceSeconds * 1000);
                return;
            }
        }

        jwksService.updatePublicJwksConfiguration(systemEvent.getTenantKey(), event.getOldClients(), event.getIdpPublicClientConfigs());
        lastUpdateTime.put(systemEvent.getTenantKey(), event.getUpdateTimestamp());
    }

    @SneakyThrows
    public void sendEvent(String tenantKey, Set<String> oldClients, Map<String, IdpPublicClientConfig> newConfigs) {
        SystemEvent systemEvent = new SystemEvent();
        systemEvent.setEventType(JWK_UPDATE);
        systemEvent.setTenantKey(tenantKey);
        systemEvent.setEventId(UUID.randomUUID().toString());
        systemEvent.setData(new UpdateClientsEvent(currentTimeMillis(), oldClients, newConfigs));
        systemEvent.setMessageSource(applicationName);
        template.send(applicationProperties.getKafkaSystemQueue(), mapper.writeValueAsString(systemEvent));
    }

    public void emitUpdateEvent(String configKey) {
        configurationService.refreshConfiguration(configKey);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateClientsEvent {
        private Long updateTimestamp;
        private Set<String> oldClients;
        private Map<String, IdpPublicClientConfig> idpPublicClientConfigs;
    }
}
