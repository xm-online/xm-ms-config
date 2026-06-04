package com.icthh.xm.ms.configuration.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.messaging.event.system.SystemEvent;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.impl.IdpConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.repository.kafka.SystemQueueConsumer;
import lombok.SneakyThrows;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.icthh.xm.ms.configuration.repository.kafka.InMemoryConfigQueueConsumer.UPDATE_IN_MEMORY;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class IdpUpdateConfigIntTest extends AbstractSpringBootTest {

    public static final String TEST_PATH = "/config/tenants/TEST_TENANT/webapp/public/idp-config-public.yml";
    public static final String JWK_TEMPLATE = "{\"keys\":[{\"kid\": \"{mockValue}\",\"n\":\"{mockValueN}\"}]}";
    public static final List<String> JWKS = List.of("/config/tenants/TEST_TENANT/config/idp/clients/TestClient1-jwks-cache.json",
        "/config/tenants/TEST_TENANT/config/idp/clients/TestClient2-jwks-cache.json");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @MockitoBean
    ConfigTopicProducer configTopicProducer;
    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean
    JwkFetcher jwkFetcher;

    @Autowired
    SystemQueueConsumer systemQueueConsumer;
    @Autowired
    ConfigurationService configurationService;

    @Autowired
    IdpConfigRepository idpConfigRepository;

    @Test
    @DirtiesContext // idp refreshable has old clients
    @SneakyThrows
    public void testUpdateJwkTtl() {
        reset(configTopicProducer, kafkaTemplate, jwkFetcher);

        mockInfrastructure();

        String publicConfig = loadFile("test-idp-config-public.yml");
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig);
        assertJwkUpdated(1, this::eventEqNoOldClients, true);
        configurationService.updateConfiguration(new Configuration(TEST_PATH, publicConfig));
        clearInvocations(configTopicProducer, kafkaTemplate, jwkFetcher);

        Thread.sleep(3100); // wait scheduler time

        verify(configTopicProducer, times(1)).notifyConfigurationChanged(any(), eq(List.of(TEST_PATH)));
        verifyNoMoreInteractions(configTopicProducer, kafkaTemplate, jwkFetcher);
    }

    private void assertJwkUpdated(int systemQueueMessages, ArgumentMatcher<String> eventMatcher, boolean isJwksShouldUpdate) {
        verify(kafkaTemplate, times(systemQueueMessages)).send(eq("system_queue"), argThat(eventMatcher));
        verify(jwkFetcher, times(1)).fetchJwk(eq("https://test1.com/certs"));
        verify(jwkFetcher, times(1)).fetchJwk(eq("https://test2.com/certs"));
        if (isJwksShouldUpdate) {
            verify(kafkaTemplate, times(1)).send(eq("in_memory_config_topic"), argThat(this::eventEqInMemory));
        }
        verifyNoMoreInteractions(configTopicProducer, kafkaTemplate, jwkFetcher);
    }

    @Test
    @DirtiesContext // idp refreshable has old clients
    @SneakyThrows
    public void testUpdateDeduplication() {
        reset(configTopicProducer, kafkaTemplate, jwkFetcher);

        mockInfrastructure();

        String publicConfig = loadFile("test-idp-config-public.yml");

        idpConfigRepository.onRefresh(TEST_PATH, publicConfig);
        verify(kafkaTemplate, times(1)).send(eq("system_queue"), argThat(this::eventEqNoOldClients));
        verify(kafkaTemplate, times(1)).send(eq("in_memory_config_topic"), argThat(this::eventEqInMemory));
        verifyNoMoreInteractions(kafkaTemplate);
        clearInvocations(kafkaTemplate);

        Thread.sleep(100);
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes

        assertJwkUpdated(2, this::eventEq, false);
        clearInvocations(configTopicProducer, kafkaTemplate, jwkFetcher);
        Thread.sleep(2100); // wait debounce time

        idpConfigRepository.onRefresh(TEST_PATH, publicConfig);
        Thread.sleep(100);
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes

        // public config updated 2 times, but jwks updated only 1 time
        assertJwkUpdated(3, this::eventEq, true);
    }

    private void mockInfrastructure() {
        when(kafkaTemplate.send(eq("system_queue"), any())).thenAnswer(invocation -> {
            systemQueueConsumer.consumeEvent(new ConsumerRecord<>("system_queue", 0, 0, "test", invocation.getArgument(1)));
            return null;
        });
        when(jwkFetcher.fetchJwk(eq("https://test1.com/certs")))
            .thenAnswer(it -> jwkConfigFromTemplate(1));
        when(jwkFetcher.fetchJwk(eq("https://test2.com/certs")))
            .thenAnswer(it -> jwkConfigFromTemplate(2));
    }

    @SneakyThrows
    private boolean eventEqNoOldClients(String s) {
        SystemEvent actual = mapper.readValue(s, SystemEvent.class);
        SystemEvent expected = mapper.readValue(loadFile("updateJwkEvent.json"), SystemEvent.class);
        Map<String, Object> actualContext = actual.getDataMap();
        actualContext.remove("updateTimestamp");
        Map<String, Object> expectedContext = expected.getDataMap();
        expectedContext.remove("updateTimestamp");
        expectedContext.put("oldClients", List.of());
        assertEquals(expectedContext, actualContext);
        assertEquals(expected.getEventType(), actual.getEventType());
        assertEquals(expected.getMessageSource(), actual.getMessageSource());
        return true;
    }

    @SneakyThrows
    private boolean eventEqInMemory(String s) {
        SystemEvent actual = mapper.readValue(s, SystemEvent.class);
        List<Map<String, String>> actualJwks = ((List<Map<String, String>>) actual.getData());
        actualJwks.sort(Comparator.comparing(map -> map.get("path")));
        List<Map<String, String>> expectedJwks = getExpectedJwks();

        assertEquals("config", actual.getMessageSource());
        assertEquals(UPDATE_IN_MEMORY, actual.getEventType());
        assertEquals(expectedJwks, actualJwks);
        return true;
    }

    private List<Map<String, String>> getExpectedJwks() {
        AtomicInteger counter = new AtomicInteger(0);
        return JWKS.stream()
            .map( path -> Map.of(
                "path", path,
                "content", jwkConfigFromTemplate(counter.incrementAndGet())
            )).toList();
    }

    private String jwkConfigFromTemplate(int number) {
        return JWK_TEMPLATE
            .replace("{mockValue}", "test" + number)
            .replace("{mockValueN}", "" + number);
    }

    @SneakyThrows
    private boolean eventEq(String s) {
        SystemEvent actual = mapper.readValue(s, SystemEvent.class);
        SystemEvent expected = mapper.readValue(loadFile("updateJwkEvent.json"), SystemEvent.class);
        Map<String, Object> actualContext = actual.getDataMap();
        actualContext.remove("updateTimestamp");
        Map<String, Object> expectedContext = expected.getDataMap();
        expectedContext.remove("updateTimestamp");
        assertEquals(expectedContext, actualContext);
        assertEquals(expected.getEventType(), actual.getEventType());
        assertEquals(expected.getMessageSource(), actual.getMessageSource());
        return true;
    }

}
