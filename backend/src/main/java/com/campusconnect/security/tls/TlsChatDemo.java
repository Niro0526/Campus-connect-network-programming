package com.campusconnect.security.tls;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * CLI demonstration entry point for Task 5 TLS secure chat connections.
 * <p>
 * Demonstrates:
 * 1. Starting a TLS chat server with server keystore.
 * 2. Connecting with a trusted client verifying the server certificate.
 * 3. Connecting to an untrusted server or with an invalid truststore to demonstrate rejection.
 */
public final class TlsChatDemo {

    private static final String DEFAULT_PASSWORD = "changeit";

    private TlsChatDemo() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();
        try {
            switch (mode) {
                case "server" -> runServer(args);
                case "client" -> runClient(args);
                case "untrusted" -> runUntrustedClient(args);
                default -> printUsage();
            }
        } catch (TlsException e) {
            System.err.println("\n[TLS SECURITY ALERT] " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("\n[ERROR] " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runServer(String[] args) throws Exception {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8443;
        Path keystore = args.length > 2 ? Path.of(args[2]) : resolveDefaultCert("server.keystore.p12");
        String password = args.length > 3 ? args[3] : DEFAULT_PASSWORD;

        System.out.println("==================================================");
        System.out.println("   Campus Connect - Secure TLS Chat Server");
        System.out.println("==================================================");
        System.out.println("Binding to port: " + port);
        System.out.println("Loading server keystore: " + keystore.toAbsolutePath().normalize());

        SSLServerSocket serverSocket = TlsServerSocketFactory.createServerSocket(
                "0.0.0.0", port, keystore, password.toCharArray());

        System.out.println("[READY] TLS Chat Server listening on " + serverSocket.getLocalSocketAddress());
        System.out.println("Waiting for secure client connections...\n");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                SSLSocket client = TlsServerSocketFactory.acceptAndHandshake(serverSocket);
                TlsConnectionInfo info = TlsClientSocketFactory.getConnectionInfo(client);

                System.out.println("--------------------------------------------------");
                System.out.println("[NEW CLIENT CONNECTED]");
                System.out.println("Remote Address: " + client.getRemoteSocketAddress());
                System.out.println("Security Status: " + info.getStatusBadge());
                System.out.println("Negotiated Protocol: " + info.protocol());
                System.out.println("Cipher Suite: " + info.cipherSuite());
                System.out.println("--------------------------------------------------");

                // Handle client in thread
                Thread clientThread = new Thread(() -> handleClientChat(client));
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (TlsException e) {
                System.err.println("[HANDSHAKE REJECTED] " + e.getMessage());
            }
        }
    }

    private static void handleClientChat(SSLSocket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println("{\"type\":\"SYSTEM\",\"message\":\"Connected securely to Campus Connect TLS Chat\"}");

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + socket.getRemoteSocketAddress() + "] >> " + line);
                // Echo message back with SECURE tag
                writer.println("{\"type\":\"CHAT_ACK\",\"received\":\"" + line.replace("\"", "\\\"") + "\"}");
            }
        } catch (Exception e) {
            System.out.println("[CLIENT DISCONNECTED] " + socket.getRemoteSocketAddress() + " (" + e.getMessage() + ")");
        }
    }

    private static void runClient(String[] args) throws Exception {
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 8443;
        Path truststore = args.length > 3 ? Path.of(args[3]) : resolveDefaultCert("server.truststore.p12");
        String password = args.length > 4 ? args[4] : DEFAULT_PASSWORD;
        String username = args.length > 5 ? args[5] : "student-" + (int) (Math.random() * 900 + 100);

        System.out.println("==================================================");
        System.out.println("   Campus Connect - Secure TLS Chat Client");
        System.out.println("==================================================");
        System.out.println("Connecting to: " + host + ":" + port);
        System.out.println("Using truststore: " + truststore.toAbsolutePath().normalize());

        try (SSLSocket socket = TlsClientSocketFactory.connect(host, port, truststore, password)) {
            TlsConnectionInfo info = TlsClientSocketFactory.getConnectionInfo(socket);

            System.out.println("\n[HANDSHAKE SUCCESSFUL]");
            System.out.println("Status: " + info.getStatusBadge());
            System.out.println("Negotiated Protocol: " + info.protocol());
            System.out.println("Cipher Suite: " + info.cipherSuite());
            System.out.println("Verified Server Identity: " + info.peerPrincipal());
            System.out.println("Common Name: " + info.commonName());
            System.out.println("--------------------------------------------------");
            System.out.println("Type a chat message and press Enter (or 'quit' to exit):");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Read welcome/system greeting
            String serverMsg = in.readLine();
            if (serverMsg != null) {
                System.out.println("[SERVER] " + serverMsg);
            }

            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                    break;
                }
                if (!input.isEmpty()) {
                    String chatJson = "{\"type\":\"CHAT\",\"room\":\"general\",\"username\":\""
                            + username + "\",\"text\":\"" + input.replace("\"", "\\\"") + "\"}";
                    out.println(chatJson);
                    String ack = in.readLine();
                    if (ack != null) {
                        System.out.println("[SERVER ACK] " + ack);
                    }
                }
            }
            System.out.println("Disconnected.");
        }
    }

    private static void runUntrustedClient(String[] args) throws Exception {
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 8443;

        System.out.println("==================================================");
        System.out.println("   Simulating Untrusted / Rogue Server Test");
        System.out.println("==================================================");
        System.out.println("Attempting TLS connection without trusting the server certificate...");

        // Create an empty temporary truststore
        Path emptyTruststore = Files.createTempFile("empty-truststore-", ".p12");
        emptyTruststore.toFile().deleteOnExit();
        java.security.KeyStore emptyKs = java.security.KeyStore.getInstance("PKCS12");
        emptyKs.load(null, "changeit".toCharArray());
        try (var os = Files.newOutputStream(emptyTruststore)) {
            emptyKs.store(os, "changeit".toCharArray());
        }

        try {
            TlsClientSocketFactory.connect(host, port, emptyTruststore, "changeit");
            System.err.println("[SECURITY FAILURE] Untrusted connection unexpectedly succeeded!");
        } catch (TlsException e) {
            System.out.println("\n[TEST PASSED] The client safely rejected the untrusted certificate:");
            System.out.println(">> " + e.getMessage());
        }
    }

    private static Path resolveDefaultCert(String filename) {
        Path[] paths = new Path[]{
                Path.of("certs", filename),
                Path.of("..", "certs", filename),
                Path.of("backend", "src", "test", "resources", "certs", filename)
        };
        for (Path p : paths) {
            if (Files.exists(p)) return p;
        }
        return Path.of("certs", filename);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  TlsChatDemo server [port] [keystorePath] [keystorePassword]");
        System.out.println("  TlsChatDemo client [host] [port] [truststorePath] [truststorePassword] [username]");
        System.out.println("  TlsChatDemo untrusted [host] [port]");
    }
}
