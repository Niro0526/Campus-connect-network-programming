package com.campusconnect.security.tls;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Factory for creating initialized {@link SSLContext} instances for Campus Connect
 * TLS chat servers and clients.
 * <p>
 * This class abstracts Java Cryptography Architecture (JCA) configuration,
 * KeyStore loading, and Key/TrustManager initialization.
 */
public final class TlsContextFactory {

    private static final String TLS_PROTOCOL = "TLS";
    private static final String PKCS12_TYPE = "PKCS12";

    private TlsContextFactory() {
    }

    /**
     * Builds an {@link SSLContext} for a TLS server using a server keystore.
     *
     * @param keystorePath     path to the server PKCS12 keystore file
     * @param keystorePassword password for the keystore
     * @return initialized SSLContext
     * @throws TlsException if loading keystore or initializing TLS context fails
     */
    public static SSLContext forServer(Path keystorePath, char[] keystorePassword) throws TlsException {
        return forServer(keystorePath, keystorePassword, null, null);
    }

    /**
     * Overload accepting String password for server keystore.
     */
    public static SSLContext forServer(Path keystorePath, String keystorePassword) throws TlsException {
        return forServer(keystorePath, keystorePassword != null ? keystorePassword.toCharArray() : null, null, null);
    }

    /**
     * Builds an {@link SSLContext} for a TLS server with optional client authentication.
     *
     * @param keystorePath       path to the server PKCS12 keystore file
     * @param keystorePassword   password for the server keystore
     * @param truststorePath     path to the server truststore (for client cert validation), or null
     * @param truststorePassword password for the truststore, or null
     * @return initialized SSLContext
     * @throws TlsException if loading keystore or initializing TLS context fails
     */
    public static SSLContext forServer(Path keystorePath, char[] keystorePassword,
                                       Path truststorePath, char[] truststorePassword) throws TlsException {
        Objects.requireNonNull(keystorePath, "keystorePath must not be null");
        KeyStore keyStore = loadKeyStore(keystorePath, keystorePassword);
        KeyStore trustStore = truststorePath != null ? loadKeyStore(truststorePath, truststorePassword) : null;
        return createSslContext(keyStore, keystorePassword, trustStore);
    }

    /**
     * Builds an {@link SSLContext} for a TLS client verifying the server with the given truststore.
     *
     * @param truststorePath     path to the client PKCS12 truststore file
     * @param truststorePassword password for the truststore
     * @return initialized SSLContext
     * @throws TlsException if loading truststore or initializing TLS context fails
     */
    public static SSLContext forClient(Path truststorePath, char[] truststorePassword) throws TlsException {
        return forClient(truststorePath, truststorePassword, null, null);
    }

    /**
     * Overload accepting String password for client truststore.
     */
    public static SSLContext forClient(Path truststorePath, String truststorePassword) throws TlsException {
        return forClient(truststorePath, truststorePassword != null ? truststorePassword.toCharArray() : null, null, null);
    }

    /**
     * Builds an {@link SSLContext} for a TLS client with optional mutual authentication (client keystore).
     *
     * @param truststorePath     path to the client PKCS12 truststore file
     * @param truststorePassword password for the truststore
     * @param keystorePath       path to optional client keystore file (mTLS), or null
     * @param keystorePassword   password for client keystore, or null
     * @return initialized SSLContext
     * @throws TlsException if loading stores or initializing TLS context fails
     */
    public static SSLContext forClient(Path truststorePath, char[] truststorePassword,
                                       Path keystorePath, char[] keystorePassword) throws TlsException {
        Objects.requireNonNull(truststorePath, "truststorePath must not be null");
        KeyStore trustStore = loadKeyStore(truststorePath, truststorePassword);
        KeyStore keyStore = keystorePath != null ? loadKeyStore(keystorePath, keystorePassword) : null;
        return createSslContext(keyStore, keystorePassword, trustStore);
    }

    /**
     * Creates an {@link SSLContext} directly from loaded {@link KeyStore} instances.
     *
     * @param keyStore    server or client key store, or null
     * @param keyPassword password for keys in keyStore, or null
     * @param trustStore  trust store containing trusted CA/certificates, or null
     * @return initialized SSLContext
     * @throws TlsException if initializing managers fails
     */
    public static SSLContext createSslContext(KeyStore keyStore, char[] keyPassword, KeyStore trustStore)
            throws TlsException {
        try {
            KeyManager[] keyManagers = null;
            if (keyStore != null) {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, keyPassword != null ? keyPassword : new char[0]);
                keyManagers = kmf.getKeyManagers();
            }

            TrustManager[] trustManagers = null;
            if (trustStore != null) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            }

            SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
            context.init(keyManagers, trustManagers, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw new TlsException("Failed to initialize SSLContext: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a {@link KeyStore} (PKCS12 by default, fallback to JKS) from the given filesystem path.
     *
     * @param path     path to the keystore file
     * @param password store password, or null
     * @return loaded KeyStore instance
     * @throws TlsException if file is missing, password invalid, or format corrupted
     */
    public static KeyStore loadKeyStore(Path path, char[] password) throws TlsException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new TlsException("Keystore file not found: " + path.getFileName());
        }

        try (InputStream stream = Files.newInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance(PKCS12_TYPE);
            try {
                keyStore.load(stream, password);
                return keyStore;
            } catch (IOException e) {
                // If loading as PKCS12 fails, check if it was JKS
                if (e.getCause() instanceof GeneralSecurityException || e.getMessage() != null && e.getMessage().contains("password")) {
                    throw new TlsException("Failed to unlock keystore '" + path.getFileName() + "': invalid password", e);
                }
                // Try fallback to JKS
                try (InputStream jksStream = Files.newInputStream(path)) {
                    KeyStore jksStore = KeyStore.getInstance("JKS");
                    jksStore.load(jksStream, password);
                    return jksStore;
                } catch (Exception ignored) {
                    throw new TlsException("Failed to parse keystore '" + path.getFileName() + "': corrupted or unsupported format", e);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new TlsException("Failed to read keystore '" + path.getFileName() + "': " + e.getMessage(), e);
        }
    }
}
