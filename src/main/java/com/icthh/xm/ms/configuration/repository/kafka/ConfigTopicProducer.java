package com.icthh.xm.ms.configuration.repository.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import com.icthh.xm.commons.config.domain.ConfigEvent;
import com.icthh.xm.commons.config.domain.ConfigurationEvent;
import com.icthh.xm.commons.logging.util.MdcUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigTopicProducer {

    private final KafkaTemplate<String, String> template;

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(new JavaTimeModule());

    @Value("${spring.application.name}")
    private String appName;

    @Value("${xm-config.kafka-config-topic}")
    private String topicName;

    public void notifyConfigurationChanged(List<ConfigurationEvent> configurations) {
        ConfigEvent event = buildEvent(MdcUtils.getRid(), configurations);
        serializeEvent(event).ifPresent(this::send);
    }

    private Optional<String> serializeEvent(Object event) {
        try {
            return Optional.ofNullable(mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Error while serializing system event: {}", event, e);
        }

        return Optional.empty();
    }

    private ConfigEvent buildEvent(String eventId, List<ConfigurationEvent> configurations) {
        ConfigEvent event = new ConfigEvent();
        event.setEventId(eventId);
        event.setConfigurations(configurations);

        return event;
    }

    private void send(String content) {
        if (StringUtils.isNotBlank(content)) {
            log.info("Sending system event to kafka-topic = '{}', data = '{}'", topicName, content);
            template.send(topicName, content);
        }
    }
}