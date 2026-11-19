package com.campusconnect.client.tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A blocking-socket client for the Campus Connect newline-delimited TCP chat protocol.
 *
 * <p>One reader thread is started per connection. Public send operations are serialized,
 * while incoming messages are delivered to the listener from that reader thread.</p>
 */
public final class TcpChatClient implements Closeable {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final String host;
    private final int port;
    private final ChatClientListener listener;
    private final Object sendLock = new Object();
    private final AtomicBoolean disconnectedNotified = new AtomicBoolean();

    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private volatile Thread readerThread;
    private volatile boolean connected;
    private String username;
    private String room;

    public TcpChatClient(String host, int port, ChatClientListener listener) {
        this.host = requireHost(host);
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("Port must be between 1 and 65535");
        this.port = port;
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public TcpChatClient(String host, int port) {
        this(host, port, new ChatClientListener() { });
    }

    public void connect() throws IOException {
        synchronized (sendLock) {
            if (connected) throw new IllegalStateException("Client is already connected");

            Socket newSocket = new Socket();
            try {
                newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
                newSocket.setTcpNoDelay(true);
                BufferedWriter newWriter = new BufferedWriter(new OutputStreamWriter(
                        newSocket.getOutputStream(), StandardCharsets.UTF_8));
                socket = newSocket;
                writer = newWriter;
                connected = true;
                disconnectedNotified.set(false);
                Thread thread = new Thread(() -> readMessages(newSocket),
                        "campus-connect-chat-reader-" + host + ":" + port);
                thread.setDaemon(true);
                readerThread = thread;
                thread.start();
            } catch (IOException | RuntimeException exception) {
                closeQuietly(newSocket);
                throw exception;
            }
        }
        listener.onConnected();
    }

    public boolean isConnected() {
        return connected;
    }

    public void joinRoom(String username, String room) throws IOException {
        String validUsername = requireText(username, "Username");
        String validRoom = requireText(room, "Room");
        synchronized (sendLock) {
            requireConnected();
            sendObject(Map.of("type", "JOIN", "room", validRoom, "username", validUsername));
            this.username = validUsername;
            this.room = validRoom;
        }
    }

    public void sendMessage(String text) throws IOException {
        String validText = Objects.requireNonNull(text, "Message text");
        synchronized (sendLock) {
            requireConnected();
            if (username == null || room == null) {
                throw new IllegalStateException("Join a room before sending messages");
            }
            sendObject(Map.of("type", "CHAT", "room", room, "username", username, "text", validText));
        }
    }

    public void leaveRoom() throws IOException {
        synchronized (sendLock) {
            if (!connected || username == null || room == null) return;
            sendObject(Map.of("type", "LEAVE", "room", room, "username", username));
            username = null;
            room = null;
        }
    }

    public void disconnect() {
        Socket socketToClose;
        synchronized (sendLock) {
            if (!connected && socket == null) return;
            if (connected && username != null && room != null) {
                try {
                    sendObject(Map.of("type", "LEAVE", "room", room, "username", username));
                } catch (IOException ignored) {
                    // Closing the socket still guarantees disconnect cleanup on the server.
                }
            }
            connected = false;
            username = null;
            room = null;
            socketToClose = socket;
            socket = null;
            writer = null;
        }
        closeQuietly(socketToClose);
        notifyDisconnected();
    }

    @Override
    public void close() {
        disconnect();
    }

    private void readMessages(Socket source) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                source.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException exception) {
            if (connected) listener.onError("CONNECTION", exception.getMessage());
        } finally {
            synchronized (sendLock) {
                if (socket == source) {
                    connected = false;
                    socket = null;
                    writer = null;
                    username = null;
                    room = null;
                }
            }
            closeQuietly(source);
            notifyDisconnected();
        }
    }

    private void dispatch(String line) {
        try {
            Map<String, String> fields = JsonLine.parse(line);
            String type = required(fields, "type");
            switch (type) {
                case "CHAT" -> listener.onMessage(new ChatMessage(
                        required(fields, "room"), required(fields, "username"), required(fields, "text")));
                case "ERROR" -> listener.onError(required(fields, "code"), required(fields, "message"));
                default -> listener.onError("PROTOCOL", "Unsupported message type: " + type);
            }
        } catch (IllegalArgumentException exception) {
            listener.onError("PROTOCOL", exception.getMessage());
        }
    }

    private void sendObject(Map<String, String> fields) throws IOException {
        writer.write(JsonLine.write(fields));
        writer.newLine();
        writer.flush();
    }

    private void requireConnected() {
        if (!connected || writer == null) throw new IllegalStateException("Client is not connected");
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing field: " + name);
        return value;
    }

    private static String requireHost(String value) {
        return requireText(value, "Host");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }

    private void notifyDisconnected() {
        if (disconnectedNotified.compareAndSet(false, true)) listener.onDisconnected();
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
            // The original connection error is more useful to the caller.
        }
    }
}
