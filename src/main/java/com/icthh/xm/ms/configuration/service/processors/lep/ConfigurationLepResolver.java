package com.icthh.xm.ms.configuration.service.processors.lep;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.lep.api.LepKeyResolver;
import com.icthh.xm.lep.api.LepMethod;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfigurationLepResolver implements LepKeyResolver {

    private final TenantContextHolder tenantContextHolder;

    @Override
    public List<String> segments(LepMethod method) {
        Configuration configuration = method.getParameter("configuration", Configuration.class);
        String path = getRelativePath(configuration);
        int index = path.lastIndexOf("/") + 1;
        String name = path.substring(index);
        return List.of(name);
    }

    @Override
    public String group(LepMethod method) {
        Configuration configuration = method.getParameter("configuration", Configuration.class);
        String path = getRelativePath(configuration);
        int index = path.lastIndexOf("/");
        String pathToConfig = path.substring(0, index);
        return LepKeyResolver.super.group(method) + "." + pathToConfig.replaceAll("/", ".");
    }

    private String getRelativePath(Configuration configuration) {
        String path = configuration.getPath();
        String tenantPrefix = TENANT_PREFIX + tenantContextHolder.getTenantKey() + "/";
        if (path.startsWith(tenantPrefix)) {
            path = path.substring(tenantPrefix.length());
        }
        return path;
    }
}
