
package com.campusconnect.server.tcp;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TcpChatTestClient {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter Student ID: ");
        String studentId = scanner.nextLine().trim();

        System.out.print("Enter Username: ");
        String username = scanner.nextLine().trim();

        try (
                Socket socket = new Socket("localhost", 5000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(
                        socket.getOutputStream(), true)
        ) {

            Thread receiveThread = new Thread(() -> {
                try {
                    String response;

                    while ((response = reader.readLine()) != null) {
                        ChatMessage message =
                                gson.fromJson(response, ChatMessage.class);
                        displayMessage(message);
                    }

                } catch (Exception e) {
                    System.out.println("\nConnection closed.");
                }
            });

            receiveThread.setDaemon(true);
            receiveThread.start();

            ChatMessage identify = new ChatMessage(
                    "IDENTIFY", studentId, username, null, null);

            writer.println(gson.toJson(identify));

            showWelcome();

            while (scanner.hasNextLine()) {

                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                if (input.equalsIgnoreCase("/help")) {
                    showHelp();
                    continue;
                }

                if (input.equalsIgnoreCase("/exit")
                        || input.equalsIgnoreCase("LEAVE")) {

                    writer.println(gson.toJson(
                            new ChatMessage("LEAVE", null)));

                    System.out.println("Leaving chat...");
                    break;
                }

                if (input.equalsIgnoreCase("/rooms")) {
                    writer.println(gson.toJson(
                            new ChatMessage("LIST_ROOMS", null)));
                    continue;
                }

                if (input.equalsIgnoreCase("/users")) {
                    writer.println(gson.toJson(
                            new ChatMessage("LIST_USERS", null)));
                    continue;
                }

                if (input.toLowerCase().startsWith("/join ")) {

                    String roomName = input.substring(6).trim();

                    if (roomName.isEmpty()) {
                        System.out.println("Room name cannot be empty.");
                        continue;
                    }

                    writer.println(gson.toJson(
                            new ChatMessage(
                                    "JOIN_ROOM", null, null,
                                    roomName, null)));
                    continue;
                }

                if (input.toLowerCase().startsWith("/pm ")) {

                    String[] parts = input.split("\\s+", 3);

                    if (parts.length < 3) {
                        System.out.println(
                                "Usage: /pm studentId message");
                        continue;
                    }

                    ChatMessage privateMessage = new ChatMessage(
                            "PRIVATE_MESSAGE", null, null, null, parts[2]);

                    privateMessage.setRecipientId(parts[1]);

                    writer.println(gson.toJson(privateMessage));
                    continue;
                }

                if (input.startsWith("/")) {
                    System.out.println("Unknown command. Type /help.");
                    continue;
                }

                writer.println(gson.toJson(
                        new ChatMessage("CHAT", null, null, null, input)));
            }

        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private static void showWelcome() {
        System.out.println("\n===== CAMPUS CONNECT CHAT =====");
        System.out.println("Welcome, you're connected!");
        System.out.println("Type /help for available commands.");
        System.out.println("==============================\n");
    }

    private static void showHelp() {
        System.out.println("\n===== CHAT COMMANDS =====");
        System.out.println("/help                 - Show commands");
        System.out.println("/users                - List online users");
        System.out.println("/rooms                - List chat rooms");
        System.out.println("/join roomName         - Join a room");
        System.out.println("/pm studentId message - Send private message");
        System.out.println("/exit                 - Disconnect");
        System.out.println("======================= \n");
    }

    private static void displayMessage(ChatMessage message) {

        if (message == null) {
            System.out.println("[SERVER] Invalid response.");
            return;
        }

        switch (message.getType()) {

            case "CHAT":
                System.out.println("\n[" + message.getRoom() + "] "
                        + message.getUsername() + ": "
                        + message.getMessage());
                break;

            case "PRIVATE_MESSAGE":
                System.out.println("\n[PRIVATE] "
                        + message.getUsername() + ": "
                        + message.getMessage());
                break;

            case "SYSTEM":
                System.out.println("\n[SYSTEM] " + message.getMessage());
                break;

            case "ERROR":
                System.out.println("\n[ERROR] " + message.getMessage());
                break;

            case "ROOM_JOINED":
                System.out.println("\n[ROOM] " + message.getMessage());
                break;

            case "ROOM_LIST":
                System.out.println("\n[ROOMS] " + message.getMessage());
                break;

            case "USER_LIST":
                System.out.println("\n===== ONLINE USERS =====");
                System.out.println(message.getMessage());
                System.out.println("=======================");
                break;

            default:
                System.out.println("\n[SERVER] " + message.getMessage());
        }
    }
}
