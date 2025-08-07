package com.icthh.xm.ms.configuration.service.factory;

import com.icthh.xm.ms.configuration.service.SpecResolveStrategy;
import com.icthh.xm.ms.configuration.service.exception.StrategyNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DefinitionResolverFactory {

    private final Map<String, SpecResolveStrategy> resolverMap;

    public DefinitionResolverFactory(List<SpecResolveStrategy> strategies) {
        this.resolverMap = strategies.stream()
                .collect(Collectors.toMap(
                        SpecResolveStrategy::getPrefix,
                        Function.identity()
                ));
    }

    public SpecResolveStrategy getDefinitionResolver(String refPrefix) {
        return Optional.ofNullable(resolverMap.get(refPrefix)).orElseThrow(() -> new StrategyNotFoundException("No resolver found for prefix: " + refPrefix));
    }
}
