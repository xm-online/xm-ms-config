package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.messaging.event.system.SystemEvent;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.impl.IdpConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.repository.kafka.SystemQueueConsumer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

public class IdpUpdateConfigIntTest extends AbstractSpringBootTest {

    public static final String TEST_PATH = "/config/tenants/TEST_TENANT/webapp/public/idp-config-public.yml";
    public static final String JWK_TEMPLATE = "{\"keys\":[{\"kid\": \"{mockValue}\",\"n\":\"{mockValueN}\"}]}";
    public static final List<String> JWKS = List.of("/config/tenants/TEST_TENANT/config/idp/clients/TestClient1-jwks-cache.json",
        "/config/tenants/TEST_TENANT/config/idp/clients/TestClient2-jwks-cache.json");

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockBean
    ConfigTopicProducer configTopicProducer;
    @MockBean
    KafkaTemplate<String, String> kafkaTemplate;
    @MockBean
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
        assertJwkUpdated(1, this::eventEqNoOldClients);
        configurationService.updateConfiguration(new Configuration(TEST_PATH, publicConfig));
        clearInvocations(configTopicProducer, kafkaTemplate, jwkFetcher);

        Thread.sleep(3100); // wait scheduler time

        verify(configTopicProducer, times(1)).notifyConfigurationChanged(any(), eq(List.of(TEST_PATH)));
        verifyNoMoreInteractions(configTopicProducer, kafkaTemplate, jwkFetcher);
    }

    private void assertJwkUpdated(int systemQueueMessages, ArgumentMatcher<String> eventMatcher) {
        verify(kafkaTemplate, times(systemQueueMessages)).send(eq("system_queue"), argThat(eventMatcher));
        verify(configTopicProducer, times(1)).notifyConfigurationChanged(any(), eq(JWKS));
        verify(jwkFetcher, times(1)).fetchJwk(eq("https://test1.com/certs"));
        verify(jwkFetcher, times(1)).fetchJwk(eq("https://test2.com/certs"));
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
        verifyNoMoreInteractions(kafkaTemplate);
        clearInvocations(kafkaTemplate);

        Thread.sleep(100);
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes

        assertJwkUpdated(2, this::eventEq);
        clearInvocations(configTopicProducer, kafkaTemplate, jwkFetcher);
        Thread.sleep(2100); // wait debounce time

        idpConfigRepository.onRefresh(TEST_PATH, publicConfig);
        Thread.sleep(100);
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes
        idpConfigRepository.onRefresh(TEST_PATH, publicConfig); // emulate 3 nodes

        // public config updated 2 times, but jwks updated only 1 time
        assertJwkUpdated(3, this::eventEq);
    }

    private void mockInfrastructure() {
        when(kafkaTemplate.send(eq("system_queue"), any())).thenAnswer(invocation -> {
            systemQueueConsumer.consumeEvent(new ConsumerRecord<>("system_queue", 0, 0, "test", invocation.getArgument(1)));
            return null;
        });
        AtomicInteger counter = new AtomicInteger(0);
        when(jwkFetcher.fetchJwk(eq("https://test1.com/certs")))
            .thenAnswer(it -> JWK_TEMPLATE
                .replace("{mockValue}", "test1")
                .replace("{mockValueN}", "" + counter.incrementAndGet()));
        when(jwkFetcher.fetchJwk(eq("https://test2.com/certs")))
            .thenAnswer(it -> JWK_TEMPLATE
                .replace("{mockValue}", "test2")
                .replace("{mockValueN}", "" + counter.incrementAndGet()));
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
