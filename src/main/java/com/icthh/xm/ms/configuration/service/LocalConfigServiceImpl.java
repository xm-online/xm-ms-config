package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.client.repository.ConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.InMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Primary
@Component
public class LocalConfigServiceImpl implements ConfigService {

    private final InMemoryRepository inMemoryRepository;

    public Map<String, String> getConfig() {
        return inMemoryRepository.getMap();
    }
}
