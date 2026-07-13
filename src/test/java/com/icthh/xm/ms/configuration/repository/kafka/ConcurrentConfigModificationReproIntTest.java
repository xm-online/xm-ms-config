package com.icthh.xm.ms.configuration.repository.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.icthh.xm.commons.config.client.api.FetchConfigurationSettings;
import com.icthh.xm.commons.config.domain.ConfigQueueEvent;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.internal.PrototypeXmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.security.internal.SpringSecurityXmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.internal.DefaultTenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.service.ConfigVersionDeserializer;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.FileService;
import com.icthh.xm.ms.configuration.service.VersionCache;
import com.icthh.xm.ms.configuration.topic.ConfigurationUpdateEventProcessor;
import lombok.SneakyThrows;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.jgit.api.Git;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.ApplicationEventPublisher;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.icthh.xm.commons.config.domain.enums.ConfigEventType.UPDATE_CONFIG;
import static com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration.createGitRepository;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class ConcurrentConfigModificationReproIntTest {

    private static final String TENANT = "TEST";
    private static final String PATH = "/config/tenants/TEST/some-config.yml";

    @Rule
    public TemporaryFolder serverGitFolder = new TemporaryFolder();
    @Rule
    public TemporaryFolder configGitFolder = new TemporaryFolder();
    @Rule
    public TemporaryFolder initTestGitFolder = new TemporaryFolder();

    private final ApplicationProperties applicationProperties = new ApplicationProperties();
    private final GitProperties gitProperties = new GitProperties();
    private final FileService fileService = new FileService(applicationProperties);

    private final TenantContextHolder tenantContextHolder = new DefaultTenantContextHolder();
    private final XmAuthenticationContextHolder authenticationContextHolder = new SpringSecurityXmAuthenticationContextHolder();
    private final XmRequestContextHolder requestContextHolder = new PrototypeXmRequestContextHolder();

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private JGitRepository jGitRepository;
    private ConfigQueueConsumer configQueueConsumer;

    @BeforeEach
    public void setUp() {
        applicationProperties.setGit(gitProperties);
        gitProperties.setCloneRepositoryOnUpdate(true);

        createGitRepository(serverGitFolder, initTestGitFolder, gitProperties);

        jGitRepository = new JGitRepository(gitProperties, new ReentrantLock(),
            tenantContextHolder, authenticationContextHolder, requestContextHolder, fileService) {
            @Override
            @SneakyThrows
            protected File createGitWorkDirectory() {
                configGitFolder.create();
                return configGitFolder.getRoot();
            }
        };

        ConfigurationService configurationService = new ConfigurationService(
            mock(MemoryConfigStorage.class),
            jGitRepository,
            mock(ConfigTopicProducer.class),
            tenantContextHolder,
            applicationProperties,
            mock(ConfigVersionDeserializer.class),
            mock(ApplicationEventPublisher.class),
            mock(VersionCache.class),
            new ReentrantLock(),
            mock(FetchConfigurationSettings.class));

        ConfigurationUpdateEventProcessor processor = new ConfigurationUpdateEventProcessor(
            tenantContextHolder, mock(LepManagementService.class), configurationService);

        configQueueConsumer = new ConfigQueueConsumer(requestContextHolder, processor);
    }

    @Test
    public void staleUpdateOnRedeployIsSkipped_repoStaysActual() {
        jGitRepository.save(new Configuration(PATH, "old_content"));
        String staleOldConfigHash = sha1Hex("old_content");
        pushExternalChange(PATH, "new_deployed_content");
        ConsumerRecord<String, String> record = updateConfigEvent(PATH, "content_from_update_message", staleOldConfigHash);
        assertDoesNotThrow(() -> configQueueConsumer.consumeEvent(record));
        assertEquals("new_deployed_content", jGitRepository.find(PATH).getData().getContent());
    }

    @SneakyThrows
    private ConsumerRecord<String, String> updateConfigEvent(String path, String content, String oldConfigHash) {
        Map<String, String> data = new HashMap<>();
        data.put("path", path);
        data.put("content", content);
        data.put("oldConfigHash", oldConfigHash);
        ConfigQueueEvent event = new ConfigQueueEvent(
            "event-id", "entity", TENANT, UPDATE_CONFIG.name(), Instant.now(), data);
        String json = objectMapper.writeValueAsString(event);
        return new ConsumerRecord<>("config-queue", 0, 0L, "key", json);
    }

    @SneakyThrows
    private void pushExternalChange(String path, String content) {
        File external = Files.createTempDirectory("external-deploy").toFile();
        Git.cloneRepository()
            .setURI(gitProperties.getUri())
            .setDirectory(external)
            .setBranch(gitProperties.getBranchName())
            .call().close();
        try (Git git = Git.open(external)) {
            git.checkout().setName(gitProperties.getBranchName()).call();
            File file = new File(external, path);
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), content);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("deploy new config repo version").call();
            git.push().call();
        }
    }
}
