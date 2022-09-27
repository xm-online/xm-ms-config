package com.icthh.xm.ms.configuration.config.lep;

import com.icthh.xm.commons.lep.commons.CommonsExecutor;
import com.icthh.xm.commons.lep.commons.CommonsService;
import com.icthh.xm.commons.lep.spring.SpringLepProcessingApplicationListener;
import com.icthh.xm.lep.api.ScopedContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LepContextListener extends SpringLepProcessingApplicationListener {

    private static final String COMMONS = "commons";

    private final CommonsService commonsService;

    @Override
    protected void bindExecutionContext(ScopedContext executionContext) {
        executionContext.setValue(COMMONS, new CommonsExecutor(commonsService));
    }
}
