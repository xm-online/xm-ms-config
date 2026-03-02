package com.icthh.xm.ms.configuration.listener;

import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.permission.inspector.PrivilegeInspector;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigQueueConsumer;
import com.icthh.xm.ms.configuration.repository.kafka.InMemoryConfigQueueConsumer;
import com.icthh.xm.ms.configuration.repository.kafka.SystemQueueConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class ApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationProperties applicationProperties;
    private final ConsumerFactory<String, String> consumerFactory;
    private final SystemQueueConsumer systemQueueConsumer;
    private final ConfigQueueConsumer configQueueConsumer;
    private final InMemoryConfigQueueConsumer inMemoryconfigQueueConsumer;
    private final XmConfigProperties xmConfigProperties;
    private final KafkaProperties kafkaProperties;
    private final PrivilegeInspector privilegeInspector;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (applicationProperties.isKafkaEnabled()) {
            createKafkaConsumers();
            privilegeInspector.readPrivileges(MdcUtils.getRid());
        } else {
            log.warn("WARNING! Privileges inspection is disabled by "
                + "configuration parameter 'application.kafka-enabled'");
        }
    }

    private void createKafkaConsumers() {
        createKafkaConsumer(applicationProperties.getKafkaSystemQueue(), systemQueueConsumer::consumeEvent);
        createKafkaConsumer(xmConfigProperties.getKafkaConfigQueue(), configQueueConsumer::consumeEvent);
        createKafkaConsumerWithUuidGroup(applicationProperties.getKafkaInMemoryConfigTopic(), inMemoryconfigQueueConsumer::consumeEvent);
    }

    private void createKafkaConsumer(String name, MessageListener<String, String> consumeEvent) {
        createKafkaConsumer(name, consumeEvent, null);
    }

    private void createKafkaConsumerWithUuidGroup(String name, MessageListener<String, String> consumeEvent) {
        createKafkaConsumer(name, consumeEvent, UUID.randomUUID().toString());
    }

    private void createKafkaConsumer(String name, MessageListener<String, String> consumeEvent, String groupId) {
        log.info("Creating kafka consumer for topic {}", name);
        ContainerProperties containerProps = new ContainerProperties(name);

        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        if (groupId != null) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, applicationProperties.getKafkaMetadataMaxAge());
        ConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);

        ConcurrentMessageListenerContainer<String, String> container =
            new ConcurrentMessageListenerContainer<>(factory, containerProps);
        container.setupMessageListener(consumeEvent);
        container.start();
        log.info("Successfully created kafka consumer for topic {}", name);
    }
}
