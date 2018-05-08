package com.icthh.xm.ms.configuration.repository.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.messaging.event.system.SystemEvent;
import com.icthh.xm.commons.messaging.event.system.SystemEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class SystemTopicProducer {

    private final KafkaTemplate<String, String> template;

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(new JavaTimeModule());

    @Value("${spring.application.name}")
    private String appName;

    @Value("${application.kafka-system-topic}")
    private String topicName;

    public void notifyConfigurationSaved(Object data) {
        sendConfigurationEvent(SystemEventType.SAVE_CONFIGURATION, data);
    }

    public void notifyConfigurationDeleted(Object data) {
        sendConfigurationEvent(SystemEventType.DELETE_CONFIGURATION, data);
    }

    private void sendConfigurationEvent(String eventType, Object data) {
        SystemEvent event = buildSystemEvent(MdcUtils.getRid(), eventType, data);
        serializeEvent(event).ifPresent(this::send);
    }

    private Optional<String> serializeEvent(SystemEvent event) {
        try {
            return Optional.ofNullable(mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Error while serializing system event: {}", event, e);
        }

        return Optional.empty();
    }

    private SystemEvent buildSystemEvent(String eventId, String eventType, Object data) {
        SystemEvent event = new SystemEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setMessageSource(appName);
        event.setData(data);

        return event;
    }

    private void send(String content) {
        if (StringUtils.isNotBlank(content)) {
            log.info("Sending system event to kafka-topic = '{}', data = '{}'", topicName, content);
            template.send(topicName, content);
        }
    }
}