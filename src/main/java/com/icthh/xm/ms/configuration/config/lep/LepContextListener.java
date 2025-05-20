package com.icthh.xm.ms.configuration.config.lep;

import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepContextFactory;
import com.icthh.xm.lep.api.LepMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LepContextListener implements LepContextFactory {

    @Override
    public BaseLepContext buildLepContext(LepMethod lepMethod) {
        return new LepContext();
    }
}
