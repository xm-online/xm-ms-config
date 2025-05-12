package com.icthh.xm.ms.configuration.config.lep;

import com.icthh.xm.commons.lep.groovy.GroovyLepEngineConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LepConfiguration extends GroovyLepEngineConfiguration {

    public LepConfiguration(@Value("${spring.application.name}") String appName) {
        super(appName);
    }

}
