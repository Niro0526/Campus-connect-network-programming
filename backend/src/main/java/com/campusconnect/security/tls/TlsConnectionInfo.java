package com.campusconnect.security.tls;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Immutable value object holding details about an established TLS session.
 * <p>
 * This object can be queried by JavaFX, Swing, or CLI interfaces to display
 * secure-connection status badges, negotiated protocols, cipher suites,
 * and verified server certificate identities.
 */
public record TlsConnectionInfo(
        boolean secure,
        String protocol,
        String cipherSuite,
        String peerHost,
        int peerPort,
        String peerPrincipal,
        String commonName
) {
    /**
     * Inspects an active {@link SSLSocket} and extracts its TLS session metadata.
     *
     * @param socket the connected TLS socket
     * @return a populated {@link TlsConnectionInfo} record
     * @throws NullPointerException if socket is null
     */
    public static TlsConnectionInfo fromSocket(SSLSocket socket) {
        Objects.requireNonNull(socket, "socket must not be null");
        SSLSession session = socket.getSession();
        if (session == null || !session.isValid()) {
            return unencrypted(socket.getInetAddress() != null ? socket.getInetAddress().getHostName() : "unknown",
                    socket.getPort());
        }

        String protocol = session.getProtocol();
        String cipherSuite = session.getCipherSuite();
        String peerHost = socket.getInetAddress() != null ? socket.getInetAddress().getHostName() : session.getPeerHost();
        int peerPort = socket.getPort();

        String principalName = "unknown";
        String cn = "unknown";
        try {
            Principal peerPrincipal = session.getPeerPrincipal();
            if (peerPrincipal != null) {
                principalName = peerPrincipal.getName();
                cn = extractCommonName(principalName);
            }
        } catch (Exception ignored) {
            // Handshake might not have exchanged peer certs or client-only mode
        }

        return new TlsConnectionInfo(true, protocol, cipherSuite, peerHost, peerPort, principalName, cn);
    }

    /**
     * Creates an unencrypted connection info representation for fallback or plaintext mode.
     */
    public static TlsConnectionInfo unencrypted(String host, int port) {
        return new TlsConnectionInfo(false, "NONE", "NONE", host, port, "NONE", "NONE");
    }

    /**
     * Returns a concise human-readable status badge text, suitable for UI labels.
     * Example: "🔒 Secure (TLSv1.3 · TLS_AES_256_GCM_SHA384)"
     */
    public String getStatusBadge() {
        if (!secure) {
            return "🔓 Insecure (Plaintext)";
        }
        return "🔒 Secure (" + protocol + " · " + cipherSuite + ")";
    }

    /**
     * Returns a full summary including verified identity.
     */
    public String getFullSummary() {
        if (!secure) {
            return "Connection to " + peerHost + ":" + peerPort + " is not encrypted.";
        }
        return "Connected securely to " + peerHost + ":" + peerPort + "\n"
                + "Protocol: " + protocol + "\n"
                + "Cipher Suite: " + cipherSuite + "\n"
                + "Verified Subject: " + peerPrincipal;
    }

    private static String extractCommonName(String dn) {
        if (dn == null) return "unknown";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return dn;
    }
}
