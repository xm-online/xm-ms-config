package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties.SshProperties;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.util.FS;

@Slf4j
@RequiredArgsConstructor
public class SshTransportConfigCallback implements TransportConfigCallback {

    private final SshProperties sshProperties;

    private SshSessionFactory getSshSessionFactory(SshProperties sshProperties) {
        return new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch jSch = super.createDefaultJSch(fs);
                jSch.addIdentity("gitIdentity",
                                 Base64.getDecoder().decode(sshProperties.getPrivateKey()),
                                 null,
                                 sshProperties.getPassPhrase().getBytes());
                return jSch;
            }
        };
    }

    @Override
    public void configure(Transport transport) {
        if (transport instanceof SshTransport) {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(getSshSessionFactory(sshProperties));
        }
    }
}
