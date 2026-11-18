package com.campusconnect.security.tls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.Objects;

/**
 * Factory for creating verified TLS client sockets for the Campus Connect chat client.
 * <p>
 * The TCP client owner can replace {@code new Socket(host, port)} with
 * {@link #connect(String, int, Path, String)} to establish an encrypted TLS connection.
 * <p>
 * If the server certificate is untrusted, expired, or fails verification, this factory
 * throws a descriptive {@link TlsException} before returning the socket.
 */
public final class TlsClientSocketFactory {

    public static final String[] SUPPORTED_PROTOCOLS = { "TLSv1.3", "TLSv1.2" };
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    private TlsClientSocketFactory() {
    }

    /**
     * Connects to a TLS chat server with strict certificate verification using the truststore.
     *
     * @param host               server hostname or IP address
     * @param port               server TLS port
     * @param truststorePath     path to client PKCS12 truststore containing the server cert
     * @param truststorePassword password for the truststore
     * @return connected and verified SSLSocket
     * @throws TlsException if TLS configuration or certificate verification fails
     * @throws IOException  if network connection or handshake fails
     */
    public static SSLSocket connect(String host, int port, Path truststorePath, String truststorePassword)
            throws TlsException, IOException {
        return connect(host, port, truststorePath, truststorePassword != null ? truststorePassword.toCharArray() : null,
                DEFAULT_CONNECT_TIMEOUT_MS);
    }

    /**
     * Connects to a TLS chat server with a custom timeout.
     *
     * @param host               server hostname or IP
     * @param port               server TLS port
     * @param truststorePath     path to client PKCS12 truststore
     * @param truststorePassword password for truststore
     * @param timeoutMs          timeout in milliseconds
     * @return connected and verified SSLSocket
     * @throws TlsException if TLS certificate validation fails
     * @throws IOException  if connection or handshake fails
     */
    public static SSLSocket connect(String host, int port, Path truststorePath, char[] truststorePassword, int timeoutMs)
            throws TlsException, IOException {
        SSLContext sslContext = TlsContextFactory.forClient(truststorePath, truststorePassword);
        return connect(host, port, sslContext, timeoutMs, true);
    }

    /**
     * Connects to a TLS chat server using an existing {@link SSLContext}.
     *
     * @param host           server host
     * @param port           server port
     * @param sslContext     initialized client SSLContext
     * @param timeoutMs      connection timeout in milliseconds
     * @param verifyEndpoint whether to enforce HTTPS/SAN endpoint identification
     * @return connected and verified SSLSocket
     * @throws TlsException if handshake or certificate verification fails
     * @throws IOException  if network connection fails
     */
    public static SSLSocket connect(String host, int port, SSLContext sslContext, int timeoutMs, boolean verifyEndpoint)
            throws TlsException, IOException {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(sslContext, "sslContext must not be null");

        Socket plainSocket = new Socket();
        try {
            plainSocket.connect(new InetSocketAddress(host, port), timeoutMs);

            SSLSocketFactory ssf = sslContext.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) ssf.createSocket(plainSocket, host, port, true);
            sslSocket.setEnabledProtocols(SUPPORTED_PROTOCOLS);

            if (verifyEndpoint) {
                SSLParameters params = sslSocket.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(params);
            }

            // Immediately execute TLS handshake so errors are caught at connection time
            try {
                sslSocket.startHandshake();
            } catch (SSLHandshakeException e) {
                sslSocket.close();
                throw translateHandshakeException(host, e);
            } catch (SSLException e) {
                sslSocket.close();
                throw new TlsException("TLS protocol error during handshake with " + host + ": " + e.getMessage(), e);
            }

            return sslSocket;
        } catch (IOException e) {
            try {
                plainSocket.close();
            } catch (IOException ignored) {
            }
            if (e instanceof TlsException) {
                throw e;
            }
            throw e;
        }
    }

    /**
     * Extracts connection information and security details from an established SSLSocket.
     *
     * @param socket connected SSLSocket
     * @return populated TlsConnectionInfo
     */
    public static TlsConnectionInfo getConnectionInfo(SSLSocket socket) {
        return TlsConnectionInfo.fromSocket(socket);
    }

    private static TlsException translateHandshakeException(String host, SSLHandshakeException exception) {
        Throwable cause = exception.getCause();
        String message = exception.getMessage() != null ? exception.getMessage() : "";

        // Check if root cause is certificate path or untrusted cert
        while (cause != null) {
            if (cause instanceof CertificateException
                    || cause.getClass().getSimpleName().contains("CertPathValidatorException")
                    || cause.getClass().getSimpleName().contains("SunCertPathBuilderException")) {
                return new TlsException("TLS handshake rejected: Untrusted server certificate from '" + host
                        + "'. The certificate was not signed by a trusted CA or is missing from the client truststore.",
                        exception);
            }
            cause = cause.getCause();
        }

        if (message.contains("PKIX path building failed") || message.contains("certificate_unknown")) {
            return new TlsException("TLS handshake rejected: Untrusted server certificate from '" + host
                    + "'. The certificate was not signed by a trusted CA or is missing from the client truststore.",
                    exception);
        }

        if (message.contains("No name matching") || message.contains("hostname")) {
            return new TlsException("TLS handshake rejected: Hostname mismatch. The server certificate does not match '"
                    + host + "'.", exception);
        }

        return new TlsException("TLS handshake failed with '" + host + "': " + message, exception);
    }
}
