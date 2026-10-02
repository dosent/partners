package com.simbirsoft.partners.access.infrastructure;

import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/** JNDI-compatible TLS socket factory trusting only the configured corporate CA. */
public final class LdapCaSocketFactory extends SSLSocketFactory {
    private static volatile SSLContext context;
    public static void configure(String caFile) {
        try (InputStream input = Files.newInputStream(Path.of(caFile))) {
            X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setCertificateEntry("simbirsoft-ca", ca);
            TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(store);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, managers.getTrustManagers(), null);
            context = ssl;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize LDAP CA trust", exception);
        }
    }
    public static SSLSocketFactory getDefault() {
        SSLContext configured = context;
        if (configured == null) throw new IllegalStateException("LDAP CA trust is not configured");
        return configured.getSocketFactory();
    }
    private SSLSocketFactory delegate() { return getDefault(); }
    private static Socket verifyHostname(Socket socket) {
        if (socket instanceof SSLSocket sslSocket) {
            javax.net.ssl.SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("LDAPS");
            sslSocket.setSSLParameters(parameters);
        }
        return socket;
    }
    @Override public String[] getDefaultCipherSuites() { return delegate().getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate().getSupportedCipherSuites(); }
    @Override public Socket createSocket(Socket s, String h, int p, boolean c) throws java.io.IOException {
        return verifyHostname(delegate().createSocket(s, h, p, c));
    }
    @Override public Socket createSocket(String h, int p) throws java.io.IOException { return verifyHostname(delegate().createSocket(h, p)); }
    @Override public Socket createSocket(String h, int p, java.net.InetAddress l, int lp) throws java.io.IOException {
        return verifyHostname(delegate().createSocket(h, p, l, lp));
    }
    @Override public Socket createSocket(java.net.InetAddress h, int p) throws java.io.IOException {
        return verifyHostname(delegate().createSocket(h, p));
    }
    @Override public Socket createSocket(java.net.InetAddress h, int p, java.net.InetAddress l, int lp)
            throws java.io.IOException { return verifyHostname(delegate().createSocket(h, p, l, lp)); }
}
