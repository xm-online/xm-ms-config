package com.icthh.xm.ms.configuration.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties.SshProperties;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.Test;

public class SshTransportConfigCallbackUnitTest {

    private static final InetSocketAddress GIT_SERVER = InetSocketAddress.createUnresolved("git.example.com", 22);

    private final PublicKey serverKey = generateKey();

    @Test
    public void acceptsAnyKeyWithoutKnownHostsWhenUnknownHostAllowed() {
        assertTrue(callback(null, true).acceptServerKey(GIT_SERVER, serverKey));
    }

    @Test
    public void rejectsAnyKeyWithoutKnownHostsWhenUnknownHostNotAllowed() {
        assertFalse(callback(null, false).acceptServerKey(GIT_SERVER, serverKey));
    }

    @Test
    public void acceptsKnownHostKey() {
        String knownHosts = "git.example.com " + PublicKeyEntry.toString(serverKey);

        assertTrue(callback(knownHosts, true).acceptServerKey(GIT_SERVER, serverKey));
    }

    @Test
    public void acceptsKnownHostKeyOnCustomPort() {
        String knownHosts = "[git.example.com]:2222 " + PublicKeyEntry.toString(serverKey);

        assertTrue(callback(knownHosts, true)
            .acceptServerKey(InetSocketAddress.createUnresolved("git.example.com", 2222), serverKey));
    }

    @Test
    public void rejectsOtherKeyOfKnownHostEvenWhenUnknownHostAllowed() {
        String knownHosts = "git.example.com " + PublicKeyEntry.toString(generateKey());

        assertFalse(callback(knownHosts, true).acceptServerKey(GIT_SERVER, serverKey));
    }

    @Test
    public void rejectsKeyOfOtherHost() {
        String knownHosts = "other.example.com " + PublicKeyEntry.toString(serverKey);

        assertFalse(callback(knownHosts, true).acceptServerKey(GIT_SERVER, serverKey));
    }

    private static SshTransportConfigCallback callback(String knownHosts, boolean acceptUnknownHost) {
        SshProperties sshProperties = new SshProperties();
        sshProperties.setKnownHosts(knownHosts);
        sshProperties.setAcceptUnknownHost(acceptUnknownHost);
        return new SshTransportConfigCallback(sshProperties);
    }

    private static PublicKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair().getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
