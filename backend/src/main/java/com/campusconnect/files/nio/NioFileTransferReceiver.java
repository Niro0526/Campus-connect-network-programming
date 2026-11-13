package com.campusconnect.files.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Validates transfer metadata, streams bytes to disk, then publishes the file. */
public final class NioFileTransferReceiver {
    private NioFileTransferReceiver() { }

    public static TransferMetadata receive(SocketChannel socket, Path destinationDirectory) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Path directory = destinationDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);

        TransferMetadata metadata = NioFileTransferProtocol.readHeader(socket);
        validateFileName(metadata.fileName());
        Path destination = directory.resolve(metadata.fileName()).normalize();
        if (!destination.getParent().equals(directory)) throw new IOException("File name escapes destination directory");
        Path temporary = directory.resolve("." + metadata.transferId() + ".part");

        boolean completed = false;
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.allocate(NioFileTransferProtocol.BUFFER_SIZE);
                long received = 0;
                while (received < metadata.fileSize()) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), metadata.fileSize() - received));
                    int count = socket.read(buffer);
                    if (count < 0) throw new IOException("Client disconnected before all file bytes arrived");
                    if (count == 0) continue;
                    received += count;
                    buffer.flip();
                    NioFileTransferProtocol.writeFully(output, buffer);
                }
                output.force(true);
            }
            // Default move semantics fail if the destination already exists.
            Files.move(temporary, destination);
            completed = true;
            return metadata;
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
        }
    }

    private static void validateFileName(String name) throws IOException {
        if (name.isBlank() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0
                || Path.of(name).isAbsolute()) {
            throw new IOException("Unsafe file name in transfer metadata");
        }
    }
}
