package com.campusconnect.security.tls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TlsHandshakeTest {

    private static final String PASSWORD = "changeit";

    private static Path resolveCertPath(String filename) throws Exception {
        Path[] candidates = new Path[]{
                Path.of("src", "test", "resources", "certs", filename),
                Path.of("backend", "src", "test", "resources", "certs", filename),
                Path.of("certs", filename),
                Path.of("..", "certs", filename)
        };
        for (Path p : candidates) {
            if (Files.exists(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        var resource = TlsHandshakeTest.class.getResource("/certs/" + filename);
        if (resource != null) {
            Path temp = Files.createTempFile("test-cert-", "-" + filename);
            temp.toFile().deleteOnExit();
            try (var in = resource.openStream()) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        }
        throw new FileNotFoundException("Test cert file not found: " + filename);
    }

    @Test
    @DisplayName("Trusted client and server complete TLS handshake and exchange chat message")
    void testTrustedHandshakeAndMessageExchange() throws Exception {
        Path serverKeystore = resolveCertPath("server.keystore.p12");
        Path clientTruststore = resolveCertPath("server.truststore.p12");

        // Bind server on loopback, ephemeral port (0)
        SSLServerSocket serverSocket = TlsServerSocketFactory.createServerSocket(
                "127.0.0.1", 0, serverKeystore, PASSWORD.toCharArray());
        int boundPort = serverSocket.getLocalPort();
        assertTrue(boundPort > 0, "Server must bind to a valid port");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch serverReady = new CountDownLatch(1);

        Future<String> serverTask = pool.submit(() -> {
            serverReady.countDown();
            try (SSLSocket accepted = (SSLSocket) serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(accepted.getOutputStream(), StandardCharsets.UTF_8), true)) {

                accepted.startHandshake();
                String receivedMsg = in.readLine();
                out.println("{\"type\":\"CHAT\",\"room\":\"general\",\"username\":\"server\",\"text\":\"ECHO:" + receivedMsg + "\"}");
                return receivedMsg;
            } finally {
                serverSocket.close();
            }
        });

        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server thread did not start in time");

        String chatMessage = "{\"type\":\"CHAT\",\"room\":\"general\",\"username\":\"alice\",\"text\":\"Hello encrypted world!\"}";

        try (SSLSocket clientSocket = TlsClientSocketFactory.connect(
                "127.0.0.1", boundPort, clientTruststore, PASSWORD.toCharArray(), 5000)) {

            // Verify connection security details
            TlsConnectionInfo connectionInfo = TlsClientSocketFactory.getConnectionInfo(clientSocket);
            assertTrue(connectionInfo.secure(), "Connection must report secure");
            assertNotNull(connectionInfo.protocol(), "Protocol must be negotiated");
            assertTrue(connectionInfo.protocol().startsWith("TLSv1."), "Protocol must be TLS 1.2 or 1.3");
            assertNotNull(connectionInfo.cipherSuite(), "Cipher suite must be present");
            assertTrue(connectionInfo.getStatusBadge().contains("Secure"), "Badge must display secure status");

            // Send chat message
            PrintWriter clientOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

            clientOut.println(chatMessage);
            String response = clientIn.readLine();

            assertNotNull(response, "Server response must not be null");
            assertTrue(response.contains("ECHO:"), "Response must echo message");
            assertTrue(response.contains("Hello encrypted world!"), "Response must contain transmitted text");
        }

        String serverReceived = serverTask.get(5, TimeUnit.SECONDS);
        assertEquals(chatMessage, serverReceived, "Server must receive exact message sent by client");

        pool.shutdownNow();
    }

    @Test
    @DisplayName("Client rejects untrusted server certificate with descriptive TlsException")
    void testUntrustedServerCertificateRejected() throws Exception {
        Path untrustedKeystore = resolveCertPath("untrusted.keystore.p12");
        Path clientTruststore = resolveCertPath("server.truststore.p12"); // Only trusts campus-server

        SSLServerSocket untrustedServer = TlsServerSocketFactory.createServerSocket(
                "127.0.0.1", 0, untrustedKeystore, PASSWORD.toCharArray());
        int boundPort = untrustedServer.getLocalPort();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            try (SSLSocket client = (SSLSocket) untrustedServer.accept()) {
                client.startHandshake();
            } catch (Exception ignored) {
                // Expected handshake failure on server side
            } finally {
                try {
                    untrustedServer.close();
                } catch (Exception ignored) {
                }
            }
        });

        // Client attempts to connect with truststore that does not include the untrusted server certificate
        TlsException exception = assertThrows(TlsException.class, () -> {
            TlsClientSocketFactory.connect("127.0.0.1", boundPort, clientTruststore, PASSWORD.toCharArray(), 5000);
        });

        assertNotNull(exception.getMessage(), "Exception must have a message");
        assertTrue(exception.getMessage().contains("Untrusted server certificate")
                        || exception.getMessage().contains("rejected")
                        || exception.getMessage().contains("handshake"),
                "Exception message must clearly state certificate verification failure. Got: " + exception.getMessage());

        // Verify that passwords or sensitive data are never in the error message
        assertFalse(exception.getMessage().contains(PASSWORD), "Exception message must never leak passwords");

        pool.shutdownNow();
    }

    @Test
    @DisplayName("Invalid keystore password throws descriptive TlsException without leaking password")
    void testInvalidPasswordThrowsClearError() throws Exception {
        Path serverKeystore = resolveCertPath("server.keystore.p12");

        TlsException exception = assertThrows(TlsException.class, () -> {
            TlsContextFactory.forServer(serverKeystore, "wrong-password-12345");
        });

        assertTrue(exception.getMessage().toLowerCase().contains("password")
                        || exception.getMessage().toLowerCase().contains("unlock")
                        || exception.getMessage().toLowerCase().contains("failed"),
                "Message should indicate password failure: " + exception.getMessage());
        assertFalse(exception.getMessage().contains("wrong-password-12345"), "Must not leak the password attempt");
    }

    @Test
    @DisplayName("Nonexistent keystore file throws clear TlsException")
    void testMissingKeystoreThrowsClearError() {
        Path missingPath = Path.of("certs", "does-not-exist.p12");

        TlsException exception = assertThrows(TlsException.class, () -> {
            TlsContextFactory.forServer(missingPath, "changeit");
        });

        assertTrue(exception.getMessage().contains("not found"),
                "Message should state file was not found: " + exception.getMessage());
    }
}
