package com.icthh.xm.ms.configuration.repository.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.config.client.repository.message.ConfigurationUpdateMessage;
import com.icthh.xm.commons.config.domain.ConfigQueueEvent;
import com.icthh.xm.commons.request.XmPrivilegedRequestContext;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.ms.configuration.topic.ConfigurationUpdateEventProcessor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.icthh.xm.commons.config.domain.enums.ConfigEventType.UPDATE_CONFIG;
import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_NAME;
import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_TYPE;
import static com.icthh.xm.ms.configuration.domain.RequestSourceType.CONFIG_QUEUE;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ConfigQueueConsumerIntTest {

    private static final String TOPIC = "config-queue";
    private static final String MESSAGE_SOURCE = "entity";
    private static final String TENANT = "TEST";
    private static final String EVENT_ID = RandomStringUtils.randomAlphanumeric(5);
    private static final Instant START_DATE = Instant.now();

    private static final String PATH = "/config/tenants/TEST/service/file";
    private static final String CONTENT = "content";
    private static final String OLD_CONFIG_HASH = "hash";

    @Mock
    private ConfigurationUpdateEventProcessor configurationUpdateEventProcessor;

    @Mock
    private XmRequestContextHolder requestContextHolder;

    @Mock
    private XmPrivilegedRequestContext privilegedContext;

    @Mock
    private ConsumerRecord<String, String> consumerRecord;

    @InjectMocks
    private ConfigQueueConsumer configQueueConsumer;

    private ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(new JavaTimeModule());

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(requestContextHolder.getPrivilegedContext()).thenReturn(privilegedContext);
    }

    @Test
    public void consumeEvent() throws IOException {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("path", PATH);
        eventData.put("content", CONTENT);
        eventData.put("oldConfigHash", OLD_CONFIG_HASH);

        var event = new ConfigQueueEvent(EVENT_ID, MESSAGE_SOURCE, TENANT, UPDATE_CONFIG.name(), START_DATE, eventData);

        when(consumerRecord.value()).thenReturn(objectMapper.writeValueAsString(event));
        when(consumerRecord.topic()).thenReturn(TOPIC);

        configQueueConsumer.consumeEvent(consumerRecord);

        verify(configurationUpdateEventProcessor).process(
            argThat(isDataWith(PATH, CONTENT, OLD_CONFIG_HASH)),
            argThat(eqString(TENANT)));
        verify(privilegedContext).putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
        verify(privilegedContext).putValue(REQUEST_SOURCE_NAME, MESSAGE_SOURCE);
        verify(privilegedContext).destroyCurrentContext();
    }

    @Test
    public void consumeEvent_invalidEventDataType_throwIOException() throws IOException {
        var event = new ConfigQueueEvent(EVENT_ID, MESSAGE_SOURCE, TENANT, UPDATE_CONFIG.name(), START_DATE, List.of());

        when(consumerRecord.value()).thenReturn(objectMapper.writeValueAsString(event));
        when(consumerRecord.topic()).thenReturn(TOPIC);

        configQueueConsumer.consumeEvent(consumerRecord);

        verifyNoMoreInteractions(configurationUpdateEventProcessor);
        verify(privilegedContext).putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
        verify(privilegedContext).putValue(REQUEST_SOURCE_NAME, MESSAGE_SOURCE);
        verify(privilegedContext).destroyCurrentContext();
    }

    @Test
    public void consumeEvent_missingHash_throwIOException() throws IOException {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("path", PATH);
        eventData.put("content", CONTENT);

        var event = new ConfigQueueEvent(EVENT_ID, MESSAGE_SOURCE, TENANT, UPDATE_CONFIG.name(), START_DATE, eventData);

        when(consumerRecord.value()).thenReturn(objectMapper.writeValueAsString(event));
        when(consumerRecord.topic()).thenReturn(TOPIC);

        configQueueConsumer.consumeEvent(consumerRecord);

        verifyNoMoreInteractions(configurationUpdateEventProcessor);
        verify(privilegedContext).putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
        verify(privilegedContext).putValue(REQUEST_SOURCE_NAME, MESSAGE_SOURCE);
        verify(privilegedContext).destroyCurrentContext();
    }

    @Test
    public void consumeEvent_unknownEventType() throws IOException {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("path", PATH);
        eventData.put("content", CONTENT);
        eventData.put("oldConfigHash", OLD_CONFIG_HASH);

        var event = new ConfigQueueEvent(EVENT_ID, MESSAGE_SOURCE, TENANT, "UNKNOWN_EVENT", START_DATE, eventData);

        when(consumerRecord.value()).thenReturn(objectMapper.writeValueAsString(event));
        when(consumerRecord.topic()).thenReturn(TOPIC);

        configQueueConsumer.consumeEvent(consumerRecord);

        verifyNoMoreInteractions(configurationUpdateEventProcessor);
        verify(privilegedContext).putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
        verify(privilegedContext).putValue(REQUEST_SOURCE_NAME, MESSAGE_SOURCE);
        verify(privilegedContext).destroyCurrentContext();
    }

    @Test
    public void consumeEvent_invalidEvent() {
        when(consumerRecord.value()).thenReturn("invalid json");
        when(consumerRecord.topic()).thenReturn(TOPIC);

        configQueueConsumer.consumeEvent(consumerRecord);

        verifyNoMoreInteractions(configurationUpdateEventProcessor);
        verify(privilegedContext).putValue(REQUEST_SOURCE_TYPE, CONFIG_QUEUE);
        verify(privilegedContext).destroyCurrentContext();
    }

    private ArgumentMatcher<String> eqString(String expected) {
        return expected::equals;
    }

    private ArgumentMatcher<ConfigurationUpdateMessage> isDataWith(String path, String content, String hash) {
        return config -> config != null
            && path.equals(config.getPath())
            && content.equals(config.getContent())
            && hash.equals(config.getOldConfigHash());
    }
}
