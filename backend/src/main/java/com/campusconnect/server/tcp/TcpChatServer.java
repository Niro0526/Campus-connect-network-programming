package com.campusconnect.server.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TcpChatServer {

    private static final int PORT = 5000;
    private static final int THREAD_POOL_SIZE = 20;

    private static final ExecutorService clientPool =
            Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private static final Set<ClientHandler> clients =
            ConcurrentHashMap.newKeySet();

    // Maps student IDs to their connected client handlers
    private static final Map<String, ClientHandler> activeClients =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("Campus Connect Chat Server started on port " + PORT);
            System.out.println("Waiting for clients...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println("Connection received: "
                        + clientSocket.getInetAddress());

                ClientHandler clientHandler =
                        new ClientHandler(clientSocket);

                clients.add(clientHandler);

                clientPool.execute(clientHandler);
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        } finally {
            clientPool.shutdown();
        }
    }

    // Register a student ID with its connected client
    public static boolean registerStudentId(
            String studentId, ClientHandler client) {

        return activeClients.putIfAbsent(studentId, client) == null;
    }

    // Remove student ID only if it belongs to this client
    public static void unregisterStudentId(
            String studentId, ClientHandler client) {

        if (studentId != null) {
            activeClients.remove(studentId, client);
        }
    }

    // Find a connected client using their student ID
    public static ClientHandler getClientByStudentId(String studentId) {
        return activeClients.get(studentId);
    }

    // Remove disconnected client
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    // Get currently connected client count
    public static int getConnectedClientCount() {
        return clients.size();
    }

    public static String getOnlineUsers() {

    return clients.stream()
            .filter(client -> client.getStudentId() != null
                    && client.getUsername() != null)
            .map(client -> client.getStudentId()
                    + " - " + client.getUsername())
            .sorted()
            .collect(Collectors.joining("\n"));
    }
}