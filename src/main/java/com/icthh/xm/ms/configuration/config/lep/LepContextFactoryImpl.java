package com.icthh.xm.ms.configuration.config.lep;

import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepContextFactory;
import com.icthh.xm.lep.api.LepMethod;
import org.springframework.stereotype.Component;

@Component
public class LepContextFactoryImpl implements LepContextFactory {

    @Override
    public BaseLepContext buildLepContext(LepMethod lepMethod) {
        return new LepContext();
    }
}
