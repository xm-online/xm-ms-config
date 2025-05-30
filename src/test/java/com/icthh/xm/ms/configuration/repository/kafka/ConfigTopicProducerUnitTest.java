package com.icthh.xm.ms.configuration.repository.kafka;

import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        MdcUtils.putRid("testRid");
        when(configProperties.getKafkaConfigTopic()).thenReturn("topic");
        producer.notifyConfigurationChanged(new ConfigVersion("commit"), Collections.singletonList("path"));

        verify(kafkaTemplate).send("topic", "{\"eventId\":\"testRid\",\"commit\":\"{\\\"externalTenantVersions\\\":{},\\\"mainVersion\\\":\\\"commit\\\"}\",\"paths\":[\"path\"]}");
    }

    @Test
    public void notifyConfigurationChangedIfNoPaths() {
        producer.notifyConfigurationChanged(new ConfigVersion("commit"), Collections.emptyList());

        verifyNoInteractions(kafkaTemplate);
    }
}
