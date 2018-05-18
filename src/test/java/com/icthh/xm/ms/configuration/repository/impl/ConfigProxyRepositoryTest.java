package com.icthh.xm.ms.configuration.repository.impl;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ConfigProxyRepositoryTest {

    @InjectMocks
    private ConfigProxyRepository configProxyRepository;
    @Mock
    private PersistenceConfigRepository persistenceConfigRepository;
    @Mock
    private ConfigTopicProducer configTopicProducer;

    @Test
    public void getMap() {
        Configuration configuration1 = new Configuration("path1", "content1");
        configProxyRepository.getStorage().put("path1", configuration1);
        configProxyRepository.getVersion().set("commit1");

        Map<String, Configuration> result = configProxyRepository.getMap(null);

        assertThat(result).containsOnlyKeys("path1");
        assertThat(result).containsValue(configuration1);
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verifyZeroInteractions(persistenceConfigRepository, configTopicProducer);
    }

    @Test
    public void getMapWithCommit() {
        Configuration configuration1 = new Configuration("path1", "content1");
        Configuration configuration2 = new Configuration("path2", "content2");
        configProxyRepository.getStorage().put("path1", configuration1);
        configProxyRepository.getVersion().set("commit1");
        when(persistenceConfigRepository.findAll()).thenReturn(new ConfigurationList("commit2", Collections.singletonList(configuration2)));
        when(persistenceConfigRepository.hasVersion("commit2")).thenReturn(true);

        Map<String, Configuration> result = configProxyRepository.getMap("commit2");

        assertThat(result).containsOnlyKeys("path1");
        assertThat(result).containsValue(configuration1);
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void getMapWithCommitFromGit() {
        Configuration configuration1 = new Configuration("path1", "content1");
        Configuration configuration2 = new Configuration("path2", "content2");
        configProxyRepository.getStorage().put("path1", configuration1);
        when(persistenceConfigRepository.findAll()).thenReturn(new ConfigurationList("commit2", Collections.singletonList(configuration2)));
        when(persistenceConfigRepository.hasVersion("commit2")).thenReturn(false);

        Map<String, Configuration> result = configProxyRepository.getMap("commit2");

        assertThat(result).containsOnlyKeys("path2");
        assertThat(result).containsValue(configuration2);
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit2");
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void findAll() {
        Configuration configuration1 = new Configuration("path1", "content1");
        configProxyRepository.getStorage().put("path1", configuration1);
        configProxyRepository.getVersion().set("commit1");

        ConfigurationList result = configProxyRepository.findAll();

        assertThat(result.getCommit()).isEqualTo("commit1");
        assertThat(result.getData()).containsExactly(configuration1);
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void find() {
        Configuration configuration1 = new Configuration("path1", "content1");
        configProxyRepository.getStorage().put("path1", configuration1);
        configProxyRepository.getVersion().set("commit1");

        ConfigurationItem result = configProxyRepository.find("path1");

        assertThat(result.getCommit()).isEqualTo("commit1");
        assertThat(result.getData()).isEqualTo(configuration1);
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void save() {
        Configuration configuration1 = new Configuration("path1", "content1");
        when(persistenceConfigRepository.save(configuration1, null)).thenReturn("commit1");

        String result = configProxyRepository.save(configuration1);

        assertThat(result).isEqualTo("commit1");
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void saveWithHash() {
        Configuration configuration1 = new Configuration("path1", "content1");
        when(persistenceConfigRepository.save(configuration1, "hash1")).thenReturn("commit1");

        String result = configProxyRepository.save(configuration1, "hash1");

        assertThat(result).isEqualTo("commit1");
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void saveAll() {
        Configuration configuration1 = new Configuration("path1", "content1");
        when(persistenceConfigRepository.saveAll(singletonList(configuration1))).thenReturn("commit1");

        String result = configProxyRepository.saveAll(singletonList(configuration1));

        assertThat(result).isEqualTo("commit1");
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void delete() {
        when(persistenceConfigRepository.delete("path1")).thenReturn("commit1");

        String result = configProxyRepository.delete("path1");

        assertThat(result).isEqualTo("commit1");
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void deleteAll() {
        when(persistenceConfigRepository.deleteAll(singletonList("path1"))).thenReturn("commit1");

        String result = configProxyRepository.deleteAll(singletonList("path1"));

        assertThat(result).isEqualTo("commit1");
        assertThat(configProxyRepository.getVersion().get()).isEqualTo("commit1");
        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void refreshInternal() {
        Configuration configuration1 = new Configuration("path1", "content1");
        when(persistenceConfigRepository.findAll()).thenReturn(new ConfigurationList("commit1", singletonList(configuration1)));

        configProxyRepository.refreshInternal();

        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void refreshAll() {
        Configuration configuration1 = new Configuration("path1", "content1");
        when(persistenceConfigRepository.findAll()).thenReturn(new ConfigurationList("commit1", singletonList(configuration1)));

        configProxyRepository.refreshAll();

        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void refreshPath() {
        Configuration configuration1 = new Configuration("path1", "content1");
        when(persistenceConfigRepository.find("path1")).thenReturn(new ConfigurationItem("commit1", configuration1));

        configProxyRepository.refreshPath("path1");

        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("path1"));
    }

    @Test
    public void refreshTenant() {
        Configuration configuration1 = new Configuration("/config/tenants/tenant/path1", "content1");
        when(persistenceConfigRepository.findAll()).thenReturn(new ConfigurationList("commit1", singletonList(configuration1)));

        configProxyRepository.refreshTenant("tenant");

        verify(configTopicProducer).notifyConfigurationChanged("commit1", singletonList("/config/tenants/tenant/path1"));
    }
}