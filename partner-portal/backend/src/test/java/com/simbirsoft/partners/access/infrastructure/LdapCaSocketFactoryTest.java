package com.simbirsoft.partners.access.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.KeyManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LdapCaSocketFactoryTest {
    @TempDir Path temporaryDirectory;

    @BeforeEach
    void clearConfiguredContext() throws Exception {
        var field = LdapCaSocketFactory.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void configuredCaEstablishesTlsAndCertificateHostnameMustMatch() throws Exception {
        var certificates = createCertificates();
        LdapCaSocketFactory.configure(certificates.trustedCa().toString());

        assertTlsHandshake(certificates.trustedServerStore(), "localhost", false);
        assertTlsHandshake(certificates.trustedServerStore(), "127.0.0.1", true);
    }

    @Test
    void certificateSignedByAnotherCaIsRejected() throws Exception {
        var certificates = createCertificates();
        LdapCaSocketFactory.configure(certificates.trustedCa().toString());

        assertTlsHandshake(certificates.untrustedServerStore(), "localhost", true);
    }

    @Test
    void missingOrInvalidCaAndUnconfiguredFactoryFailClosed() throws Exception {
        assertThrows(IllegalStateException.class, LdapCaSocketFactory::getDefault);
        assertThrows(IllegalStateException.class,
                () -> LdapCaSocketFactory.configure(temporaryDirectory.resolve("missing-ca.crt").toString()));

        Path invalidCa = temporaryDirectory.resolve("invalid-ca.crt");
        Files.writeString(invalidCa, "synthetic invalid certificate");
        assertThrows(IllegalStateException.class, () -> LdapCaSocketFactory.configure(invalidCa.toString()));

        assertThrows(IllegalStateException.class, LdapCaSocketFactory::getDefault);
    }

    private void assertTlsHandshake(Path serverStore, String host, boolean rejected) throws Exception {
        SSLServerSocket server = serverSocket(serverStore);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var accepted = executor.submit(() -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.startHandshake();
                }
                return null;
            });
            try (SSLSocket client = (SSLSocket) new LdapCaSocketFactory().createSocket(host, server.getLocalPort())) {
                if (rejected) {
                    assertThrows(SSLHandshakeException.class, client::startHandshake);
                } else {
                    client.startHandshake();
                    assertTrue(client.getSession().isValid());
                }
            }
            if (!rejected) accepted.get();
            else {
                try {
                    accepted.get();
                } catch (ExecutionException expectedPeerFailure) {
                    // The server observes the client's expected rejection during the same handshake.
                }
            }
        } finally {
            server.close();
            executor.shutdownNow();
        }
    }

    private SSLServerSocket serverSocket(Path storePath) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storePath)) {
            store.load(input, "changeit".toCharArray());
        }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, "changeit".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        SSLServerSocket socket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(
                0, 10, InetAddress.getLoopbackAddress());
        socket.setNeedClientAuth(false);
        return socket;
    }

    private Certificates createCertificates() throws Exception {
        Path trustedCaStore = temporaryDirectory.resolve("trusted-ca.p12");
        Path trustedCa = temporaryDirectory.resolve("trusted-ca.crt");
        Path trustedServer = createSignedServer("trusted", trustedCaStore, trustedCa);
        Path otherCaStore = temporaryDirectory.resolve("other-ca.p12");
        Path otherCa = temporaryDirectory.resolve("other-ca.crt");
        Path untrustedServer = createSignedServer("other", otherCaStore, otherCa);
        return new Certificates(trustedCa, trustedServer, untrustedServer);
    }

    private Path createSignedServer(String name, Path caStore, Path caCertificate) throws Exception {
        Path serverStore = temporaryDirectory.resolve(name + "-server.p12");
        Path request = temporaryDirectory.resolve(name + "-server.csr");
        Path signedCertificate = temporaryDirectory.resolve(name + "-server.crt");
        keytool("-genkeypair", "-alias", "ca", "-dname", "CN=" + name + " Test CA", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-ext", "bc=ca:true", "-ext", "ku=keyCertSign",
                "-keystore", caStore.toString(), "-storetype", "PKCS12", "-storepass", "changeit",
                "-keypass", "changeit", "-noprompt");
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", caStore.toString(), "-storepass",
                "changeit", "-file", caCertificate.toString());
        keytool("-genkeypair", "-alias", "server", "-dname", "CN=localhost", "-keyalg", "RSA", "-keysize",
                "2048", "-validity", "2", "-ext", "SAN=dns:localhost", "-keystore", serverStore.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit", "-keypass", "changeit", "-noprompt");
        keytool("-certreq", "-alias", "server", "-keystore", serverStore.toString(), "-storepass", "changeit",
                "-file", request.toString(), "-ext", "SAN=dns:localhost");
        keytool("-gencert", "-alias", "ca", "-keystore", caStore.toString(), "-storepass", "changeit", "-infile",
                request.toString(), "-outfile", signedCertificate.toString(), "-rfc", "-validity", "2", "-ext",
                "SAN=dns:localhost", "-ext", "EKU=serverAuth");
        keytool("-importcert", "-alias", "ca", "-file", caCertificate.toString(), "-keystore", serverStore.toString(),
                "-storepass", "changeit", "-noprompt");
        keytool("-importcert", "-alias", "server", "-file", signedCertificate.toString(), "-keystore",
                serverStore.toString(), "-storepass", "changeit", "-noprompt");
        return serverStore;
    }

    private void keytool(String... arguments) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var command = new java.util.ArrayList<String>();
        command.add(executable);
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new IOException("keytool failed (" + exitCode + "): " + output);
    }

    private record Certificates(Path trustedCa, Path trustedServerStore, Path untrustedServerStore) { }
}
