package com.campusconnect.server.tcp;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private static final Gson gson = new Gson();

    private final Socket clientSocket;
    private PrintWriter writer;

    private String studentId;
    private String username;
    private String room;

    private boolean registered = false;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {

        try (
                Socket socket = clientSocket;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))
        ) {

            writer = new PrintWriter(socket.getOutputStream(), true);

            sendMessage(new ChatMessage(
                    "CONNECTED",
                    "Connected to Campus Connect TCP Chat Server."));

            // First JSON message must identify the user
            String firstLine = reader.readLine();

            if (firstLine == null) {
                return;
            }

            ChatMessage identification = parseMessage(firstLine);

            if (identification == null
                    || !"IDENTIFY".equals(identification.getType())) {

                sendMessage(new ChatMessage(
                        "ERROR",
                        "First message must be IDENTIFY."));
                return;
            }

            studentId = identification.getStudentId();
            username = identification.getUsername();

            if (!isValidStudentId(studentId)
                    || !isValidUsername(username)) {

                sendMessage(new ChatMessage(
                        "ERROR",
                        "Invalid student ID or username."));
                return;
            }

            // Prevent simultaneous duplicate student IDs
            if (!TcpChatServer.registerStudentId(studentId, this)) {

                sendMessage(new ChatMessage(
                        "ERROR",
                        "This student ID is already connected."));
                return;
            }

            registered = true;

            sendMessage(new ChatMessage(
                    "IDENTIFIED",
                    studentId,
                    username,
                    null,
                    "Identity accepted."));

            System.out.println(username + " (" + studentId + ") connected.");

            ChatRoomManager.joinRoom(this, "lobby");

            String line;

            while ((line = reader.readLine()) != null) {

                ChatMessage message = parseMessage(line);

                if (message == null) {
                    sendMessage(new ChatMessage(
                            "ERROR",
                            "Invalid JSON message."));
                    continue;
                }

                handleMessage(message);
            }

        } catch (IOException e) {

            System.out.println("Connection error for " + username
                    + ": " + e.getMessage());

        } finally {

            ChatRoomManager.leaveRoom(this);

            TcpChatServer.removeClient(this);

            if (registered) {
                TcpChatServer.unregisterStudentId(studentId, this);
            }

            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket.");
            }

            System.out.println("Disconnected: " + username
                    + ". Remaining clients: "
                    + TcpChatServer.getConnectedClientCount());
        }
    }

    private void handleMessage(ChatMessage message) {

        String type = message.getType();

        if (type == null) {
            sendMessage(new ChatMessage(
                    "ERROR", "Message type is required."));
            return;
        }

        switch (type.toUpperCase()) {

            case "CHAT":
                handleChat(message);
                break;

            case "PRIVATE_MESSAGE":
                handlePrivateMessage(message);
                break; 

            case "JOIN_ROOM":
                ChatRoomManager.joinRoom(this, message.getRoom());
                break;

            case "LIST_ROOMS":
                ChatRoomManager.sendRoomList(this);
                break;
            
            case "LIST_USERS":
                    handleListUsers();
                break;    

            case "LEAVE":
                sendMessage(new ChatMessage(
                        "INFO", "Disconnecting from chat."));
                closeConnection();
                break;   

            default:
                sendMessage(new ChatMessage(
                        "ERROR", "Unsupported message type: " + type));
        }
    }

    private void handleChat(ChatMessage message) {

        String text = message.getMessage();

        if (text == null || text.trim().isEmpty()) {
            sendMessage(new ChatMessage(
                    "ERROR", "Chat message cannot be empty."));
            return;
        }

        if (text.length() > 2000) {
            sendMessage(new ChatMessage(
                    "ERROR", "Message exceeds 2000 characters."));
            return;
        }

        if (room == null) {
            sendMessage(new ChatMessage(
                    "ERROR", "You are not in a chat room."));
            return;
        }

        ChatMessage chatMessage = new ChatMessage(
                "CHAT",
                studentId,
                username,
                room,
                text.trim());

        System.out.println("[" + room + "] "
                + username + ": " + text.trim());

        ChatRoomManager.broadcastToRoom(room, chatMessage, this);
    }

    private ChatMessage parseMessage(String json) {
    try {
        return gson.fromJson(json, ChatMessage.class);
    } catch (RuntimeException e) {
        return null;
    }
}

    private boolean isValidStudentId(String id) {

        return id != null
                && id.trim().matches("[a-zA-Z0-9_-]{2,20}");
    }

    private boolean isValidUsername(String name) {

        return name != null
                && name.trim().matches("[a-zA-Z0-9 _-]{2,30}");
    }

    public synchronized void sendMessage(ChatMessage message) {

        if (writer != null) {
            writer.println(gson.toJson(message));
        }
    }

    public String getStudentId() {
        return studentId;
    }

    public String getUsername() {
        return username;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    private void closeConnection() {

        try {
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing client connection.");
        }
    }
private void handlePrivateMessage(ChatMessage message) {

    String recipientId = message.getRecipientId();
    String text = message.getMessage();

    if (recipientId == null || recipientId.trim().isEmpty()) {
        sendMessage(new ChatMessage(
                "ERROR", "Recipient student ID is required."));
        return;
    }

    if (text == null || text.trim().isEmpty()) {
        sendMessage(new ChatMessage(
                "ERROR", "Private message cannot be empty."));
        return;
    }

    if (text.length() > 2000) {
        sendMessage(new ChatMessage(
                "ERROR", "Message exceeds 2000 characters."));
        return;
    }

    ClientHandler recipient =
            TcpChatServer.getClientByStudentId(recipientId.trim());

    if (recipient == null) {
        sendMessage(new ChatMessage(
                "ERROR", "Recipient is offline or not connected."));
        return;
    }

    ChatMessage privateMessage = new ChatMessage(
            "PRIVATE_MESSAGE",
            studentId,
            username,
            null,
            text.trim());

    privateMessage.setRecipientId(recipientId.trim());

    recipient.sendMessage(privateMessage);

    sendMessage(new ChatMessage(
            "INFO",
            "Private message sent successfully."));
}

    private void handleListUsers() {

        String users = TcpChatServer.getOnlineUsers();

        if (users.isEmpty()) {
            sendMessage(new ChatMessage(
                    "USER_LIST",
                    "No users are currently online."));
            return;
        }

        sendMessage(new ChatMessage(
                "USER_LIST",
                users));
    }



}