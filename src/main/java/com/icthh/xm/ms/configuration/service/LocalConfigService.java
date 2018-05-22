package com.icthh.xm.ms.configuration.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.icthh.xm.commons.config.client.api.AbstractConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Primary
@Component
public class LocalConfigService extends AbstractConfigService {

    private final DistributedConfigRepository inMemoryRepository;

    @Override
    public Map<String, Configuration> getConfigurationMap(String commit) {
        return inMemoryRepository.getMap(commit);
    }

    @Override
    public Map<String, Configuration> getConfigurationMap(String commit, Collection<String> paths) {
        Map<String, Configuration> map = getConfigurationMap(commit);
        return paths.stream().collect(toMap(identity(), map::get));
    }

}
