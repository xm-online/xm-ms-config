package com.icthh.xm.ms.configuration.config.lep;

import com.icthh.xm.commons.config.client.config.XmConfigTenantConfiguration;
import com.icthh.xm.commons.lep.TenantScriptStorage;
import com.icthh.xm.commons.lep.groovy.GroovyLepEngineConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
public class LepEngineConfiguration extends GroovyLepEngineConfiguration {

    @Value("${application.lep.tenant-script-storage}")
    private TenantScriptStorage tenantScriptStorageType;

    public LepEngineConfiguration(@Value("${spring.application.name}") String appName) {
        super(appName);
    }

    @Override
    protected TenantScriptStorage getTenantScriptStorageType() {
        return tenantScriptStorageType;
    }

}
