package com.icthh.xm.ms.configuration.repository.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.config.domain.ConfigQueueEvent;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_NAME;
import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_TYPE;
import static com.icthh.xm.ms.configuration.domain.RequestSourceType.CONFIG_QUEUE;

@Slf4j
@Component
public class InMemoryConfigQueueConsumer {

    public static final String UPDATE_IN_MEMORY = "UPDATE_IN_MEMORY";

    private final ConfigurationService configurationService;
    private final XmRequestContextHolder requestContextHolder;
    private final ObjectMapper objectMapper;

    public InMemoryConfigQueueConsumer(ConfigurationService configurationService,
                                       XmRequestContextHolder requestContextHolder) {
        this.configurationService = configurationService;
        this.requestContextHolder = requestContextHolder;
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());
    }

    /**
     * Consume config queue event message.
     * @param message the system queue event message
     */
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
            case UPDATE_IN_MEMORY:
                List<Configuration> configurations = objectMapper.convertValue(event.getData(),
                    new TypeReference<List<Configuration>>() {});
                configurationService.updateConfigurationInMemory(configurations);
                break;
            default:
                log.info("System event ignored with type='{}', source='{}', event_id='{}'",
                    event.getEventType(), event.getMessageSource(), event.getEventId());
                break;
        }
    }

    private void initRequestContextSourceType() {
        requestContextHolder.getPrivilegedContext().putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
    }

    private void initRequestContextSourceName(String source) {
        requestContextHolder.getPrivilegedContext().putValue(REQUEST_SOURCE_NAME, source);
    }
}
