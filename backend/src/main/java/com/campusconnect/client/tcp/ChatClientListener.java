package com.campusconnect.client.tcp;

/** Receives events produced by a {@link TcpChatClient}. */
public interface ChatClientListener {
    default void onConnected() {
    }

    default void onMessage(ChatMessage message) {
    }

    default void onError(String code, String message) {
    }

    default void onDisconnected() {
    }
}
