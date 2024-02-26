package com.icthh.xm.ms.configuration.service.processors.lep;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.lep.SeparatorSegmentedLepKeyResolver;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.lep.api.LepKey;
import com.icthh.xm.lep.api.LepManagerService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.commons.GroupMode;
import com.icthh.xm.lep.api.commons.SeparatorSegmentedLepKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.icthh.xm.commons.lep.XmLepConstants.EXTENSION_KEY_GROUP_MODE;
import static com.icthh.xm.commons.lep.XmLepConstants.EXTENSION_KEY_SEPARATOR;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;

@Component
@RequiredArgsConstructor
public class ConfigurationLepResolver extends SeparatorSegmentedLepKeyResolver {

    private final TenantContextHolder tenantContextHolder;

    @Override
    protected LepKey resolveKey(SeparatorSegmentedLepKey inBaseKey, LepMethod method, LepManagerService managerService) {
        Configuration configuration = this.getRequiredParam(method, "configuration", Configuration.class);

        String path = configuration.getPath();
        String tenantPrefix = TENANT_PREFIX + tenantContextHolder.getTenantKey() + "/";
        if (path.startsWith(tenantPrefix)) {
            path = path.substring(tenantPrefix.length());
        }

        int index = path.lastIndexOf("/") + 1;
        String name = translateToLepConvention(path.substring(index));
        String separator = inBaseKey.getSeparator();
        String pathToConfig = path.substring(0, index);
        String group = inBaseKey.getGroupKey().getId() + separator + pathToConfig.replaceAll("/", separator) +
                inBaseKey.getSegments()[inBaseKey.getGroupSegmentsSize()];

        SeparatorSegmentedLepKey baseKey = new SeparatorSegmentedLepKey(group, EXTENSION_KEY_SEPARATOR, EXTENSION_KEY_GROUP_MODE);
        GroupMode groupMode = new GroupMode.Builder().prefixAndIdIncludeGroup(baseKey.getGroupSegmentsSize()).build();
        return baseKey.append(name, groupMode);
    }

}
