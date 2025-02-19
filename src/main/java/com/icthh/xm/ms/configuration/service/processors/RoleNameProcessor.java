package com.icthh.xm.ms.configuration.service.processors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * The {@link RoleNameProcessor} class.
 * To roles.yml added field name. But microservices with old commons does not support this field.
 * Update to new commons require migration to lep engine. As temporary solution we add this processor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(HIGHEST_PRECEDENCE)
public class RoleNameProcessor implements TenantConfigurationProcessor {

    private static final String ROLE_CONFIG_PATH = "/config/tenants/{tenantKey}/roles.yml";

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ApplicationProperties applicationProperties;

    @Override
    public boolean isSupported(Configuration configuration) {
        if (applicationProperties.getRoleNameProcessorDisabled()) {
            return false;
        }
        return matcher.match(ROLE_CONFIG_PATH, configuration.getPath());
    }

    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage,
                                                    Set<Configuration> configToReprocess,
                                                    Map<String, Set<Configuration>> externalConfigs) {
        try {
            Map<String, Map<String, Object>> roles = mapper.readValue(configuration.getContent(), Map.class);
            var rolesWithName = roles.entrySet().stream().filter(it -> it.getValue().containsKey("name")).collect(toList());
            if (rolesWithName.isEmpty()) {
                return emptyList();
            }

            rolesWithName.forEach(it -> it.getValue().remove("name"));
            return List.of(new Configuration(configuration.getPath(), mapper.writeValueAsString(roles)));
        } catch (Exception e) {
            log.error("Error parse roles.yml", e);
            return emptyList();
        }

    }

}
