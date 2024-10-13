package com.icthh.xm.ms.configuration.config;

import static org.mockito.Mockito.mock;

import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.config.client.service.TenantAliasService;
import com.icthh.xm.commons.lep.groovy.GroovyLepEngineConfiguration;
import com.icthh.xm.commons.lep.spring.LepUpdateMode;
import com.icthh.xm.commons.logging.config.LoggingConfigService;
import com.icthh.xm.commons.logging.config.LoggingConfigServiceStub;
import com.icthh.xm.ms.configuration.service.LepContextCastIntTest.TestLepService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class TestLepConfiguration extends GroovyLepEngineConfiguration {

    public TestLepConfiguration(@Value("${spring.application.name}") String appName) {
        super(appName);
    }

    @Override
    public LepUpdateMode lepUpdateMode() {
        return LepUpdateMode.SYNCHRONOUS;
    }

    @Bean
    public LoggingConfigService LoggingConfigService() {
        return new LoggingConfigServiceStub();
    }

    @Bean
    public TenantAliasService commonsTenantAliasService() {
        return new TenantAliasService(mock(CommonConfigRepository.class), mock(TenantListRepository.class));
    }

    @Bean
    public TestLepService testLepService() {
        return new TestLepService();
    }

}
