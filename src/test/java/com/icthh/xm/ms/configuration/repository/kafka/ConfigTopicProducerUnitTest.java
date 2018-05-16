package com.icthh.xm.ms.configuration.repository.kafka;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class ConfigTopicProducerUnitTest {

    @InjectMocks
    private ConfigTopicProducer producer;
    @Mock
    private KafkaTemplate kafkaTemplate;
    @Mock
    private XmConfigProperties configProperties;

    @Test
    public void notifyConfigurationChanged() {
        when(configProperties.getKafkaConfigTopic()).thenReturn("topic");
        producer.notifyConfigurationChanged("commit", Collections.singletonList("path"));

        verify(kafkaTemplate).send("topic", "{\"commit\":\"commit\",\"paths\":[\"path\"]}");
    }

    @Test
    public void notifyConfigurationChangedIfNoPaths() {
        when(configProperties.getKafkaConfigTopic()).thenReturn("topic");
        producer.notifyConfigurationChanged("commit", Collections.emptyList());

        verifyZeroInteractions(kafkaTemplate);
    }
}