package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.icthh.xm.ms.configuration.repository.impl.MultiGitRepository.EXTERNAL_TENANTS_CONFIG;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiGitRepositoryIntTest extends AbstractSpringBootTest {

    public static final String EXT_CONTENT = "Some test ext content";
    public static final String MAIN_CONTENT = "Some test main content";
    public static final String EXT_PATH = "/config/tenants/EXT/ext.yml";
    public static final String MAIN_PATH = "/config/tenants/MAIN/main.yml";

    @Autowired
    public MultiGitRepository multiGitRepository;

    @Autowired
    public PersistenceConfigRepository jGitRepository;

    @Rule
    public TemporaryFolder extTenantGitFolder = new TemporaryFolder();

    public String extVersion;

    @Before
    @SneakyThrows
    public void setUp() {
        File externalTenantGitRoot = extTenantGitFolder.getRoot();
        try (Git git = Git.init().setDirectory(externalTenantGitRoot).call()) {
            new File(externalTenantGitRoot, "/config/tenants/EXT/").mkdirs();
            File file = new File(externalTenantGitRoot, EXT_PATH);
            file.createNewFile();
            Files.write(file.toPath(), EXT_CONTENT.getBytes(UTF_8));
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit().setMessage("Initial commit").call();
            extVersion = commit.name();
        }

        jGitRepository.save(new Configuration(MAIN_PATH, MAIN_CONTENT));
    }

    @SneakyThrows
    public String getExtVersion() {
        File externalTenantGitRoot = extTenantGitFolder.getRoot();
        try (Git git = Git.init().setDirectory(externalTenantGitRoot).call()) {
            git.checkout().setName("master").setForce(true).call();
            return git.getRepository().resolve("HEAD").name();
        }
    }

    @After
    public void after() {
        removeExtTenantConfig();
        extTenantGitFolder.delete();
    }

    @Test
    public void hasVersion() {
        String mainVersion = jGitRepository.getCurrentVersion().getMainVersion();
        assertTrue(multiGitRepository.hasVersion(new ConfigVersion(mainVersion)));
        assertFalse(multiGitRepository.hasVersion(getVersion()));
        applyExtTenantConfig();
    }

    private ConfigVersion getVersion() {
        String mainVersion = jGitRepository.getCurrentVersion().getMainVersion();
        return new ConfigVersion(
            mainVersion,
            Map.of("EXT", new ConfigVersion(getExtVersion()))
        );
    }

    @Test
    public void findAll() {
        ConfigurationList all = multiGitRepository.findAll();
        assertEquals(1, all.getData().size());
        assertEquals(MAIN_CONTENT, all.getData().get(0).getContent());
        assertEquals(MAIN_PATH, all.getData().get(0).getPath());
        applyExtTenantConfig();
        all = multiGitRepository.findAll();
        assertEquals(3, all.getData().size());
        List<Configuration> extConfigs = all.getData();
        assertEquals(EXT_CONTENT, extConfigs.get(0).getContent());
        assertEquals(EXTERNAL_TENANTS_CONFIG, extConfigs.get(1).getPath());
        assertEquals(getExternalTenantsConfig(), extConfigs.get(1).getContent());
        assertEquals(EXT_PATH, extConfigs.get(0).getPath());
        assertEquals(MAIN_CONTENT, extConfigs.get(2).getContent());
        assertEquals(MAIN_PATH, extConfigs.get(2).getPath());
    }

    @Test
    public void find() {
        ConfigurationItem mainConfig = multiGitRepository.find(MAIN_PATH);
        String mainVersion = jGitRepository.getCurrentVersion().getMainVersion();
        assertEquals(new ConfigVersion(mainVersion), mainConfig.getVersion());
        assertEquals(MAIN_CONTENT, mainConfig.getData().getContent());
        assertEquals(MAIN_PATH, mainConfig.getData().getPath());

        assertThrows(
            FileNotFoundException.class,
            () -> multiGitRepository.find(EXT_PATH)
        );

        applyExtTenantConfig();

        ConfigurationItem extConfig = multiGitRepository.find(EXT_PATH);
        assertEquals(getVersion(), extConfig.getVersion());
        assertEquals(EXT_CONTENT, extConfig.getData().getContent());
    }

    @Test
    public void saveAll() {
        applyExtTenantConfig();
        multiGitRepository.saveAll(List.of(
            new Configuration(EXT_PATH, "new ext content"),
            new Configuration(MAIN_PATH, "new main content")
        ));
        ConfigurationItem mainConfig = multiGitRepository.find(MAIN_PATH);
        assertEquals(getVersion(), mainConfig.getVersion());
        assertEquals("new main content", mainConfig.getData().getContent());
        ConfigurationItem extConfig = multiGitRepository.find(EXT_PATH);
        assertEquals(getVersion(), extConfig.getVersion());
        assertEquals("new ext content", extConfig.getData().getContent());

        removeExtTenantConfig();

        assertThrows(
            FileNotFoundException.class,
            () -> multiGitRepository.find(EXT_PATH)
        );
    }

    @Test
    public void save() {
        applyExtTenantConfig();
        multiGitRepository.save(new Configuration(EXT_PATH, "new ext content"));
        ConfigurationItem mainConfig = multiGitRepository.find(EXT_PATH);
        assertEquals(getVersion(), mainConfig.getVersion());
        assertEquals("new ext content", mainConfig.getData().getContent());

        removeExtTenantConfig();

        assertThrows(
            FileNotFoundException.class,
            () -> multiGitRepository.find(EXT_PATH)
        );
    }

    @Test
    @SneakyThrows
    public void deleteAll() {
        File externalTenantGitRoot = extTenantGitFolder.getRoot();
        applyExtTenantConfig();
        assertTrue(new File(extTenantGitFolder.getRoot(), EXT_PATH).exists());
        try (Git git = Git.init().setDirectory(externalTenantGitRoot).call()) {
            Set<String> added = git.status().call().getAdded();
            assertTrue(added.isEmpty());
        }
        var version = multiGitRepository.deleteAll(List.of(EXT_PATH, MAIN_PATH));
        assertEquals(getVersion(), version);
        try (Git git = Git.init().setDirectory(externalTenantGitRoot).call()) {
            Set<String> added = git.status().call().getAdded();
            assertTrue(added.contains(EXT_PATH.substring(1)));
        }
        assertThrows(
            FileNotFoundException.class,
            () -> multiGitRepository.find(EXT_PATH)
        );
        assertThrows(
            FileNotFoundException.class,
            () -> multiGitRepository.find(MAIN_PATH)
        );
    }

    @Test
    @SneakyThrows
    public void delete() {
        File externalTenantGitRoot = extTenantGitFolder.getRoot();
        applyExtTenantConfig();
        assertTrue(new File(extTenantGitFolder.getRoot(), EXT_PATH).exists());
        try (Git git = Git.init().setDirectory(externalTenantGitRoot).call()) {
            Set<String> added = git.status().call().getAdded();
            assertTrue(added.isEmpty());
        }
        var version = multiGitRepository.delete(EXT_PATH);
        assertEquals(getVersion(), version);
        try (Git git = Git.init().setDirectory(externalTenantGitRoot).call()) {
            Set<String> added = git.status().call().getAdded();
            assertTrue(added.contains(EXT_PATH.substring(1)));
        }
        assertThrows(
            FileNotFoundException.class,
            () -> multiGitRepository.find(EXT_PATH)
        );
        ConfigurationItem mainConfig = multiGitRepository.find(MAIN_PATH);
        assertEquals(getVersion(), mainConfig.getVersion());
        assertEquals(MAIN_CONTENT, mainConfig.getData().getContent());
    }

    @Test
    public void getCurrentVersion() {
        applyExtTenantConfig();
        assertEquals(getVersion(), multiGitRepository.getCurrentVersion());
    }

    private void applyExtTenantConfig() {
        multiGitRepository.save(new Configuration(
            EXTERNAL_TENANTS_CONFIG,
            getExternalTenantsConfig()
        ));
    }

    private String getExternalTenantsConfig() {
        return "external-tenants:\n" +
            "  EXT:\n" +
            "    uri: " + extTenantGitFolder.getRoot().getAbsolutePath() + "\n" +
            "    branchName: master";
    }

    private void removeExtTenantConfig() {
        multiGitRepository.delete(EXTERNAL_TENANTS_CONFIG);
    }
}
