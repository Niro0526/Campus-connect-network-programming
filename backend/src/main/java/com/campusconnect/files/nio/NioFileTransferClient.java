package com.campusconnect.files.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Sends one bounded file over a blocking NIO SocketChannel. */
public final class NioFileTransferClient {
    private NioFileTransferClient() { }

    public static TransferMetadata send(Path source, String host, int port) throws IOException {
        Path file = source.toAbsolutePath().normalize();
        long size = java.nio.file.Files.size(file);
        if (!java.nio.file.Files.isRegularFile(file)) throw new IOException("Source is not a regular file");
        if (size > NioFileTransferProtocol.MAX_FILE_BYTES) {
            throw new IOException("File exceeds the 25 MiB transfer limit");
        }
        String name = file.getFileName().toString();
        TransferMetadata metadata = new TransferMetadata(UUID.randomUUID(), name, size);

        try (SocketChannel socket = SocketChannel.open()) {
            socket.configureBlocking(true);
            socket.connect(new java.net.InetSocketAddress(host, port));
            NioFileTransferProtocol.writeHeader(socket, metadata);
            try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate(NioFileTransferProtocol.BUFFER_SIZE);
                long sent = 0;
                while (sent < size) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), size - sent));
                    int count = input.read(buffer);
                    if (count < 0) throw new IOException("Source file changed during transfer");
                    if (count == 0) continue;
                    buffer.flip();
                    NioFileTransferProtocol.writeFully(socket, buffer);
                    sent += count;
                }
            }
            socket.shutdownOutput();
        }
        return metadata;
    }
}
