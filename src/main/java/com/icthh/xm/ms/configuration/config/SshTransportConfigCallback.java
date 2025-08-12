package com.icthh.xm.ms.configuration.config;

import static org.apache.sshd.common.auth.UserAuthMethodFactory.PUBLIC_KEY;

import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties.SshProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

@Slf4j
@RequiredArgsConstructor
public class SshTransportConfigCallback implements TransportConfigCallback {

    private static final String SSH_TEMP_DIR = "ssh-temp-dir";
    private final SshProperties sshProperties;
    private final Path temporaryDirectory = getTempDirectory();

    @SneakyThrows
    private static Path getTempDirectory() {
        return Files.createTempDirectory(SSH_TEMP_DIR);
    }

    @PreDestroy
    public void destroy() {
        try {
            if (Files.exists(temporaryDirectory)) {
                try (Stream<Path> walk = Files.walk(temporaryDirectory)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.warn("Failed to delete path: {}", path, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.error("Failed to clean up temporary directory: {}", temporaryDirectory, e);
        }
    }

    private SshSessionFactory getSshSessionFactory() {
        return new SshdSessionFactoryBuilder()
            .setPreferredAuthentications(PUBLIC_KEY)
            .setDefaultKeysProvider(ignoredSshDirBecauseWeUseAnInMemorySetOfKeyPairs -> loadKeyPairs())
            .setHomeDirectory(temporaryDirectory.toFile())
            .setSshDirectory(temporaryDirectory.toFile())
            .setServerKeyDatabase((ignoredHomeDir, ignoredSshDir) -> new ServerKeyDatabase() {
                @Override
                public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
                    return Collections.emptyList();
                }

                @Override
                public boolean accept(String connectAddress, InetSocketAddress remoteAddress,
                                      PublicKey serverKey, Configuration config, CredentialsProvider provider) {
                    return sshProperties.isAcceptKey();
                }
            })
            .build(new JGitKeyCache());
    }

    @SneakyThrows
    private Iterable<KeyPair> loadKeyPairs() {
        var pkBytes = Base64.getDecoder().decode(sshProperties.getPrivateKey());
        ByteArrayInputStream pk = new ByteArrayInputStream(pkBytes);
        return SecurityUtils.loadKeyPairIdentities(null,null, pk,
            (session, resourceKey, retryIndex) -> sshProperties.getPassPhrase());
    }

    @Override
    public void configure(Transport transport) {
        if (transport instanceof SshTransport sshTransport) {
            sshTransport.setSshSessionFactory(getSshSessionFactory());
        }
    }
}
