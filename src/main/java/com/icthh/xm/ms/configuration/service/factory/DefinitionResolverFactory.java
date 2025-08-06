package com.icthh.xm.ms.configuration.service.factory;

import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.DefinitionResolveStrategy;
import com.icthh.xm.ms.configuration.service.exception.StrategyNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefinitionResolverFactory {

    private final List<DefinitionResolveStrategy> definitionResolveStrategies;

    public DefinitionResolveStrategy getDefinitionResolver(String refPrefix) {
        return definitionResolveStrategies.stream()
            .filter(definitionResolveStrategy -> definitionResolveStrategy.support(refPrefix))
            .findFirst()
            .orElseThrow(() -> new StrategyNotFoundException("Not found strategy for " + refPrefix));
    }
}
