package com.icthh.xm.ms.configuration.repository.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import com.icthh.xm.commons.config.domain.ConfigEvent;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigVersionMixIn;
import com.icthh.xm.ms.configuration.utils.ConfigPathUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigTopicProducer {

    private final KafkaTemplate<String, String> template;
    private final XmConfigProperties configProperties;

    private final ObjectMapper mapper = JsonMapper.builder()
        .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL)
        )
        .addMixIn(ConfigVersion.class, ConfigVersionMixIn.class)
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build();

    @Value("${xm-config.kafka-config-topic}")
    private String topicName;

    public void notifyConfigurationChanged(ConfigVersion version, List<String> paths) {
        if (CollectionUtils.isNotEmpty(paths)) {
            ConfigEvent event = buildEvent(MdcUtils.getRid(), version, paths);
            log.info("prepared ConfigEvent: commit = {}, paths.count = {}, top paths: {}",
                version, paths.size(), ConfigPathUtils.printPathsWithLimit(paths));
            serializeEvent(event).ifPresent(this::send);
        }
    }

    private Optional<String> serializeEvent(Object event) {
        try {
            return Optional.ofNullable(mapper.writeValueAsString(event));
        } catch (JacksonException e) {
            log.warn("Error while serializing system event: {}", event, e);
        }

        return Optional.empty();
    }

    @SneakyThrows
    private ConfigEvent buildEvent(String eventId, ConfigVersion version, List<String> paths) {
        ConfigEvent event = new ConfigEvent();
        event.setEventId(eventId);
        String versionJson = this.mapper.writeValueAsString(version);
        event.setCommit(versionJson);
        event.setPaths(new HashSet<>(paths));

        return event;
    }

    private void send(String content) {
        if (StringUtils.isNotBlank(content)) {
            log.info("Sending system event to kafka-topic = '{}', data.length = '{}'",
                     configProperties.getKafkaConfigTopic(), content.length());
            template.send(configProperties.getKafkaConfigTopic(), content);
        }
    }
}
