package com.icthh.xm.ms.configuration.listener;

import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.permission.inspector.PrivilegeInspector;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.repository.kafka.SystemQueueConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class ApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationProperties applicationProperties;
    private final ConsumerFactory<String, String> consumerFactory;
    private final SystemQueueConsumer systemQueueConsumer;
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
        createSystemConsumer(applicationProperties.getKafkaSystemQueue(), systemQueueConsumer::consumeEvent);
    }

    private void createSystemConsumer(String name, MessageListener<String, String> consumeEvent) {
        log.info("Creating kafka consumer for topic {}", name);
        ContainerProperties containerProps = new ContainerProperties(name);

        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        ConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);

        ConcurrentMessageListenerContainer<String, String> container =
            new ConcurrentMessageListenerContainer<>(factory, containerProps);
        container.setupMessageListener(consumeEvent);
        container.start();
        log.info("Successfully created kafka consumer for topic {}", name);
    }
}
