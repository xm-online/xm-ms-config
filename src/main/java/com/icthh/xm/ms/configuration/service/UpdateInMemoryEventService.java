package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.messaging.event.system.SystemEvent;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.icthh.xm.ms.configuration.repository.kafka.InMemoryConfigQueueConsumer.UPDATE_IN_MEMORY;

@Slf4j
@Component
public class UpdateInMemoryEventService {

    private final ObjectMapper mapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
            )
        .disable(FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private final KafkaTemplate<String, String> template;
    private final ApplicationProperties applicationProperties;
    private final String applicationName;

    public UpdateInMemoryEventService(KafkaTemplate<String, String> template,
                                      ApplicationProperties applicationProperties,
                                      @Value("${spring.application.name}")
                                      String applicationName) {
        this.template = template;
        this.applicationProperties = applicationProperties;
        this.applicationName = applicationName;
    }

    @SneakyThrows
    public void sendEvent(String tenantKey, Map<String, Configuration> jwks) {
        SystemEvent systemEvent = new SystemEvent();
        systemEvent.setEventType(UPDATE_IN_MEMORY);
        systemEvent.setTenantKey(tenantKey);
        systemEvent.setEventId(UUID.randomUUID().toString());
        systemEvent.setData(new ArrayList<>(jwks.values()));
        systemEvent.setMessageSource(applicationName);
        template.send(applicationProperties.getKafkaInMemoryConfigTopic(), mapper.writeValueAsString(systemEvent));
    }
}
