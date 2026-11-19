package com.campusconnect.server.tcp;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoomManager {

    private static final Map<String, Set<ClientHandler>> rooms =
            new ConcurrentHashMap<>();

    public static boolean joinRoom(ClientHandler client, String roomName) {

        if (roomName == null || roomName.trim().isEmpty()) {
            client.sendMessage(new ChatMessage(
                    "ERROR", "Room name cannot be empty."));
            return false;
        }

        String room = roomName.trim().toLowerCase();

        if (!room.matches("[a-zA-Z0-9_-]{1,30}")) {
            client.sendMessage(new ChatMessage(
                    "ERROR", "Invalid room name."));
            return false;
        }

        String previousRoom = client.getRoom();

        if (room.equals(previousRoom)) {
            client.sendMessage(new ChatMessage(
                    "INFO", "You are already in this room."));
            return true;
        }

        leaveRoom(client);

        Set<ClientHandler> members =
                rooms.computeIfAbsent(
                        room,
                        key -> ConcurrentHashMap.newKeySet());

        members.add(client);
        client.setRoom(room);

        client.sendMessage(new ChatMessage(
                "ROOM_JOINED",
                client.getStudentId(),
                client.getUsername(),
                room,
                "Joined room " + room));

        broadcastToRoom(
                room,
                new ChatMessage(
                        "SYSTEM",
                        client.getStudentId(),
                        client.getUsername(),
                        room,
                        client.getUsername() + " joined the room."),
                client);

        return true;
    }

    public static void leaveRoom(ClientHandler client) {

        String room = client.getRoom();

        if (room == null) {
            return;
        }

        Set<ClientHandler> members = rooms.get(room);

        if (members != null) {
            members.remove(client);

            broadcastToRoom(
                    room,
                    new ChatMessage(
                            "SYSTEM",
                            client.getStudentId(),
                            client.getUsername(),
                            room,
                            client.getUsername() + " left the room."),
                    client);

            if (members.isEmpty()) {
                rooms.remove(room, members);
            }
        }

        client.setRoom(null);
    }

    public static void broadcastToRoom(
            String room,
            ChatMessage message,
            ClientHandler sender) {

        Set<ClientHandler> members = rooms.get(room);

        if (members == null) {
            return;
        }

        for (ClientHandler client : members) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    public static void sendRoomList(ClientHandler client) {

        StringBuilder list = new StringBuilder();

        for (Map.Entry<String, Set<ClientHandler>> entry : rooms.entrySet()) {

            if (list.length() > 0) {
                list.append(", ");
            }

            list.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue().size());
        }

        client.sendMessage(new ChatMessage(
                "ROOM_LIST",
                list.length() == 0 ? "No active rooms." : list.toString()));
    }
}