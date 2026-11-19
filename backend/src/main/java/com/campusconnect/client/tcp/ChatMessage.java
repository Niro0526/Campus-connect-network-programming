package com.campusconnect.client.tcp;

/**
 * A chat message received from the TCP chat server.
 *
 * @param room the room that produced the message
 * @param username the sender's username
 * @param text the message body
 */
public record ChatMessage(String room, String username, String text) {
    public ChatMessage {
        if (room == null || room.isBlank()) throw new IllegalArgumentException("Room is required");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (text == null) throw new IllegalArgumentException("Message text is required");
    }
}
