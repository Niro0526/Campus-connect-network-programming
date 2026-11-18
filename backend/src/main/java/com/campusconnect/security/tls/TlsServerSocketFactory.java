package com.campusconnect.security.tls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory for creating configured TLS server sockets for the Campus Connect chat server.
 * <p>
 * The TCP server owner can replace {@code new ServerSocket(port)} with
 * {@link #createServerSocket(int, Path, String)} to enable end-to-end TLS encryption
 * without introducing TLS logic into chat room or message handling.
 */
public final class TlsServerSocketFactory {

    /** Modern secure TLS protocols enforced on all server sockets. */
    public static final String[] SUPPORTED_PROTOCOLS = { "TLSv1.3", "TLSv1.2" };

    private TlsServerSocketFactory() {
    }

    /**
     * Creates and binds an {@link SSLServerSocket} on all network interfaces (0.0.0.0).
     *
     * @param port             listening port
     * @param keystorePath     path to server PKCS12 keystore
     * @param keystorePassword password for keystore
     * @return bound SSLServerSocket
     * @throws TlsException if TLS context initialization fails
     * @throws IOException  if socket binding fails
     */
    public static SSLServerSocket createServerSocket(int port, Path keystorePath, String keystorePassword)
            throws TlsException, IOException {
        return createServerSocket("0.0.0.0", port, keystorePath, keystorePassword != null ? keystorePassword.toCharArray() : null);
    }

    /**
     * Creates and binds an {@link SSLServerSocket} on the specified bind address.
     *
     * @param bindHost         host/IP to bind to
     * @param port             listening port
     * @param keystorePath     path to server PKCS12 keystore
     * @param keystorePassword password for keystore
     * @return bound SSLServerSocket
     * @throws TlsException if TLS context initialization fails
     * @throws IOException  if socket binding fails
     */
    public static SSLServerSocket createServerSocket(String bindHost, int port, Path keystorePath, char[] keystorePassword)
            throws TlsException, IOException {
        SSLContext sslContext = TlsContextFactory.forServer(keystorePath, keystorePassword);
        return createServerSocket(bindHost, port, sslContext);
    }

    /**
     * Creates and binds an {@link SSLServerSocket} using a pre-configured {@link SSLContext}.
     *
     * @param bindHost   host/IP to bind to
     * @param port       listening port
     * @param sslContext initialized SSLContext
     * @return bound SSLServerSocket
     * @throws IOException if socket creation or binding fails
     */
    public static SSLServerSocket createServerSocket(String bindHost, int port, SSLContext sslContext)
            throws IOException {
        Objects.requireNonNull(sslContext, "sslContext must not be null");
        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.setEnabledProtocols(SUPPORTED_PROTOCOLS);
        serverSocket.setNeedClientAuth(false); // standard 1-way TLS: server identifies to client
        serverSocket.bind(new InetSocketAddress(bindHost, port));
        return serverSocket;
    }

    /**
     * Helper to accept an incoming client connection on an {@link SSLServerSocket}
     * and complete the TLS handshake immediately.
     * <p>
     * Calling this helper ensures handshake failures are surfaced with clear messages
     * at connection time, keeping message processing free of TLS failure states.
     *
     * @param serverSocket the listening SSL server socket
     * @return connected and handshaked SSLSocket
     * @throws IOException if network error or TLS handshake failure occurs
     */
    public static SSLSocket acceptAndHandshake(SSLServerSocket serverSocket) throws IOException {
        SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
        try {
            clientSocket.setEnabledProtocols(SUPPORTED_PROTOCOLS);
            clientSocket.startHandshake();
            return clientSocket;
        } catch (IOException e) {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
            throw new TlsException("Incoming TLS handshake failed from " + clientSocket.getRemoteSocketAddress()
                    + ": " + e.getMessage(), e);
        }
    }
}
