package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.api.LepEngineSession;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.ms.configuration.service.processors.lep.ConfigurationLepResolver;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_NAME;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static java.util.Collections.emptyList;

@Slf4j
@Component
@LepService(group = "processors")
@ConditionalOnProperty("application.lep.processor-enabled")
@RequiredArgsConstructor
public class LepConfigProcessor implements TenantConfigurationProcessor {

    private final TenantContextHolder tenantContextHolder;
    private final XmAuthenticationContextHolder authContextHolder;
    private final LepManagementService lepManager;
    private final AntPathMatcher matcher = new AntPathMatcher();
    @Setter(onMethod = @__(@Autowired))
    private LepConfigProcessor self;

    private static final String TENANT_CONFIG_PATTERN = TENANT_PREFIX + "{" + TENANT_NAME + "}/**/*";

    @Override
    public boolean isSupported(Configuration configuration) {
        return runWithLepContext(configuration, () -> self.isSupportedWithResolver(configuration), false);
    }

    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage,
                                                    Set<Configuration> configToReprocess,
                                                    Map<String, Set<Configuration>> externalConfigs) {
        return runWithLepContext(configuration,
                () -> self.processConfigurationWithResolver(configuration, originalStorage, targetStorage),
                emptyList());
    }

    @LogicExtensionPoint(value = "IsSupported", resolver = ConfigurationLepResolver.class)
    public boolean isSupportedWithResolver(Configuration configuration) {
        return self.isSupportedForAll(configuration);
    }

    @LogicExtensionPoint(value = "IsSupported")
    public boolean isSupportedForAll(Configuration configuration) {
        return false;
    }

    @LogicExtensionPoint(value = "Process", resolver = ConfigurationLepResolver.class)
    public List<Configuration> processConfigurationWithResolver(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage) {
        return self.processConfigurationForAll(configuration, originalStorage, targetStorage);
    }

    @LogicExtensionPoint(value = "Process")
    public List<Configuration> processConfigurationForAll(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage) {
        return emptyList();
    }

    private <T> T runWithLepContext(Configuration configuration, Supplier<T> task, T defaultValue) {
        if (!matcher.match(TENANT_CONFIG_PATTERN, configuration.getPath())) {
            return defaultValue;
        }
        String tenantKey = matcher.extractUriTemplateVariables(TENANT_CONFIG_PATTERN, configuration.getPath()).get(TENANT_NAME);
        return tenantContextHolder.getPrivilegedContext().execute(TenantContextUtils.buildTenant(tenantKey), () -> {
            try (LepEngineSession context = lepManager.beginThreadContext()) {
                return task.get();
            } catch (Throwable e) {
                log.error("Error process configuration", e);
                throw e;
            }
        });
    }


}
