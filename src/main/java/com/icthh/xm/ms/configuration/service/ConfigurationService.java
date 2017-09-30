package com.icthh.xm.ms.configuration.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.HazelcastRepository;
import com.icthh.xm.ms.configuration.repository.JGitRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ConfigurationService {

    private final HazelcastRepository hazelcastRepository;

    private final JGitRepository jGitRepository;

    public ConfigurationService(HazelcastRepository hazelcastRepository, JGitRepository jGitRepository) {
        this.hazelcastRepository = hazelcastRepository;
        this.jGitRepository = jGitRepository;

        hazelcastRepository.saveAll(jGitRepository.findAll());
    }

    public void createConfiguration(Configuration configuration) {
        jGitRepository.save(configuration);
        hazelcastRepository.save(configuration);
    }

    public void updateConfiguration(Configuration configuration) {
        jGitRepository.save(configuration);
        hazelcastRepository.save(configuration);
    }

    public Optional<Configuration> findConfiguration(String path) {
        return Optional.ofNullable(hazelcastRepository.find(path));
    }

    public void deleteConfiguration(String path) {
        jGitRepository.delete(path);
        hazelcastRepository.delete(path);
    }

    public void refreshConfigurations() {
        hazelcastRepository.saveAll(jGitRepository.findAll());
    }

    public void createConfigurations(List<MultipartFile> files) {
        List<Configuration> configurations = files.stream().map(this::toConfiguration).collect(toList());
        jGitRepository.saveAll(configurations);
        hazelcastRepository.saveAll(configurations);
    }

    @SneakyThrows
    private Configuration toConfiguration(MultipartFile file) {
        return new Configuration(file.getOriginalFilename(), IOUtils.toString(file.getInputStream(), UTF_8));
    }

}
