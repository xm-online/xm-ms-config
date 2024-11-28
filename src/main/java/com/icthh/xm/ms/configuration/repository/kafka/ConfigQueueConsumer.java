package com.icthh.xm.ms.configuration.repository.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.config.client.repository.message.ConfigurationUpdateMessage;
import com.icthh.xm.commons.config.domain.ConfigQueueEvent;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.ms.configuration.topic.ConfigurationUpdateEventProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_NAME;
import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_TYPE;
import static com.icthh.xm.ms.configuration.domain.RequestSourceType.CONFIG_QUEUE;

@Slf4j
@Component
public class ConfigQueueConsumer {

    private static final String UPDATE_CONFIG = "UPDATE_CONFIG";

    private final ConfigurationUpdateEventProcessor configurationUpdateEventProcessor;
    private final XmRequestContextHolder requestContextHolder;
    private final ObjectMapper objectMapper;

    public ConfigQueueConsumer(XmRequestContextHolder requestContextHolder,
                               ConfigurationUpdateEventProcessor configurationUpdateEventProcessor) {
        this.requestContextHolder = requestContextHolder;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());
        this.configurationUpdateEventProcessor = configurationUpdateEventProcessor;
    }

    /**
     * Consume config queue event message.
     * @param message the system queue event message
     */
    @Retryable(maxAttemptsExpression = "${application.config-queue-retry.max-attempts:3}",
        backoff = @Backoff(delayExpression = "${application.config-queue-retry.delay:10000}",
            multiplierExpression = "${application.config-queue-retry.multiplier:2}"))
    public void consumeEvent(ConsumerRecord<String, String> message) {
        MdcUtils.putRid();
        initRequestContextSourceType();
        try {
            log.info("Consume config event from topic [{}]", message.topic());
            ConfigQueueEvent event = objectMapper.readValue(message.value(), ConfigQueueEvent.class);
            initRequestContextSourceName(event.getMessageSource());

            log.info("Process config event from topic [{}], type='{}', source='{}', event_id ='{}'",
                message.topic(), event.getEventType(), event.getMessageSource(), event.getEventId());
            processEventByType(event);

        } catch (IOException e) {
            log.error("Message for queue: [{}] has incorrect format: '{}' ", message.topic(), message.value(), e);
        } finally {
            MdcUtils.removeRid();
            requestContextHolder.getPrivilegedContext().destroyCurrentContext();
        }
    }

    private void processEventByType(ConfigQueueEvent event) throws IOException {
        switch (event.getEventType().toUpperCase()) {
            case UPDATE_CONFIG:
                ConfigurationUpdateMessage message = buildConfigUpdateMessage(event.getData());
                configurationUpdateEventProcessor.process(message, event.getTenantKey());
                break;
            default:
                log.info("System event ignored with type='{}', source='{}', event_id='{}'",
                    event.getEventType(), event.getMessageSource(), event.getEventId());
                break;
        }
    }

    private ConfigurationUpdateMessage buildConfigUpdateMessage(Object eventData) throws IOException {
        if (!(eventData instanceof Map<?, ?>)) {
            throw new IOException("Invalid event data type");
        }
        Map<String, String> data = (Map<String, String>) eventData;
        String path = data.get("path");
        String content = data.get("content");
        String oldConfigHash = data.get("oldConfigHash");

        if (path == null || content == null || oldConfigHash == null) {
            throw new IOException("Event data has missing or null values");
        }
        return new ConfigurationUpdateMessage(path, content, oldConfigHash);
    }

    private void initRequestContextSourceType() {
        requestContextHolder.getPrivilegedContext().putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
    }

    private void initRequestContextSourceName(String source) {
        requestContextHolder.getPrivilegedContext().putValue(REQUEST_SOURCE_NAME, source);
    }
}
