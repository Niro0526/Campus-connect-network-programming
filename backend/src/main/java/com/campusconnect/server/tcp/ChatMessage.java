package com.campusconnect.server.tcp;

public class ChatMessage {

    private String type;
    private String studentId;
    private String username;
    private String room;
    private String message;
    private String recipientId;

    public ChatMessage() {
    }

    public ChatMessage(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public ChatMessage(
            String type,
            String studentId,
            String username,
            String room,
            String message) {

        this.type = type;
        this.studentId = studentId;
        this.username = username;
        this.room = room;
        this.message = message;
    }

    public String getType() {
        return type;
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

    public String getMessage() {
        return message;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    
    public String getRecipientId() {
    return recipientId;
}

public void setRecipientId(String recipientId) {
    this.recipientId = recipientId;
}
}