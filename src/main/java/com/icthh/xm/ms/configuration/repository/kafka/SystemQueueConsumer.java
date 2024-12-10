package com.icthh.xm.ms.configuration.repository.kafka;

import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_NAME;
import static com.icthh.xm.ms.configuration.config.RequestContextKeys.REQUEST_SOURCE_TYPE;
import static com.icthh.xm.ms.configuration.domain.RequestSourceType.SYSTEM_QUEUE;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.messaging.event.system.SystemEvent;
import com.icthh.xm.commons.messaging.event.system.SystemEventType;
import com.icthh.xm.commons.permission.domain.Privilege;
import com.icthh.xm.commons.permission.domain.mapper.PrivilegeMapper;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.ms.configuration.service.PrivilegeService;
import com.icthh.xm.ms.configuration.service.UpdateJwkEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Slf4j
@Service
public class SystemQueueConsumer {

    public static final String JWK_UPDATE = "JWK_UPDATE";

    private final PrivilegeService privilegeService;
    private final XmRequestContextHolder requestContextHolder;
    private final UpdateJwkEventService jwkEventService;

    private void initRequestContextSourceType() {
        requestContextHolder.getPrivilegedContext().putValue(REQUEST_SOURCE_TYPE, SYSTEM_QUEUE);
    }

    private void initRequestContextSourceName(String source) {
        requestContextHolder.getPrivilegedContext().putValue(REQUEST_SOURCE_NAME, source);
    }

    /**
     * Consume system queue event message.
     *
     * @param message the system queue event message
     */
    @Retryable(maxAttemptsExpression = "${application.retry.max-attempts}",
        backoff = @Backoff(delayExpression = "${application.retry.delay}",
            multiplierExpression = "${application.retry.multiplier}"))
    public void consumeEvent(ConsumerRecord<String, String> message) {
        MdcUtils.putRid();
        initRequestContextSourceType();
        try {
            log.info("Consume system event from topic [{}]", message.topic());
            ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.registerModule(new JavaTimeModule());
            try {
                SystemEvent event = mapper.readValue(message.value(), SystemEvent.class);
                initRequestContextSourceName(event.getMessageSource());

                log.info("Process system event from topic [{}], type='{}', source='{}', event_id ='{}'",
                         message.topic(), event.getEventType(), event.getMessageSource(), event.getEventId());

                switch (event.getEventType().toUpperCase()) {
                    case SystemEventType.MS_PRIVILEGES:
                        onEventMsPrivileges(event);
                        break;
                    case JWK_UPDATE:
                        jwkEventService.consumerEvent(event);
                        break;
                    default:
                        log.info("System event ignored with type='{}', source='{}', event_id='{}'",
                                 event.getEventType(), event.getMessageSource(), event.getEventId());
                        break;
                }
            } catch (IOException e) {
                log.error("System queue message has incorrect format: '{}' ", message.value(), e);
            }
        } finally {
            MdcUtils.removeRid();
            requestContextHolder.getPrivilegedContext().destroyCurrentContext();
        }
    }

    private void onEventMsPrivileges(SystemEvent event) {
        final String appName = getRequiredAppName(event);
        final Optional<Set<Privilege>> appPrivileges = getAppPrivileges(event, appName);

        // update app privileges and sync deleted permissions
        appPrivileges.ifPresent(privileges -> privilegeService.updatePrivileges(appName, privileges));
    }

    private static String getRequiredAppName(SystemEvent event) {
        String appName = event.getMessageSource();
        if (StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("Event '" + event.getEventType() + "' messageSource can't be blank");
        }
        return appName;
    }

    private static Optional<Set<Privilege>> getAppPrivileges(SystemEvent event, String appName) {
        // get privileges
        Object privilegesValue = event.getDataMap().get("privileges");
        if (privilegesValue == null) {
            log.info("Privileges from event '" + event.getEventType() + "' are null, app/ms: '{}'", appName);
            return Optional.empty();
        }

        // parse privileges
        String privilegesYml = String.valueOf(privilegesValue);
        if (StringUtils.isBlank(privilegesYml)) {
            log.info("Privileges yaml from event '" + event.getEventType() + "' are blank, app/ms: '{}'", appName);
            return Optional.empty();
        }
        Map<String, Set<Privilege>> commonPrivileges = PrivilegeMapper.ymlToPrivileges(privilegesYml);
        Set<Privilege> appPrivileges = commonPrivileges.get(appName);
        if (appPrivileges == null) {
            appPrivileges = Collections.emptySet();
        }

        // get application privileges
        return Optional.of(appPrivileges);
    }

}
