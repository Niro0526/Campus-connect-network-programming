package com.campusconnect.files.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Binary framing shared by the NIO file sender and receiver. */
final class NioFileTransferProtocol {
    static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    static final int BUFFER_SIZE = 16 * 1024;
    private static final int MAGIC = 0x43434654; // "CCFT"
    private static final byte VERSION = 1;
    private static final int MAX_NAME_BYTES = 255;

    private NioFileTransferProtocol() { }

    static void writeHeader(WritableByteChannel channel, TransferMetadata metadata) throws IOException {
        byte[] name = metadata.fileName().getBytes(StandardCharsets.UTF_8);
        if (name.length == 0 || name.length > MAX_NAME_BYTES) {
            throw new IOException("File name must use 1 to 255 UTF-8 bytes");
        }

        ByteBuffer header = ByteBuffer.allocate(Integer.BYTES + 1 + 16 + Short.BYTES + name.length + Long.BYTES);
        header.putInt(MAGIC).put(VERSION);
        header.putLong(metadata.transferId().getMostSignificantBits());
        header.putLong(metadata.transferId().getLeastSignificantBits());
        header.putShort((short) name.length).put(name).putLong(metadata.fileSize()).flip();
        writeFully(channel, header);
    }

    static TransferMetadata readHeader(ReadableByteChannel channel) throws IOException {
        ByteBuffer prefix = ByteBuffer.allocate(Integer.BYTES + 1 + 16 + Short.BYTES);
        readFully(channel, prefix);
        prefix.flip();
        if (prefix.getInt() != MAGIC) throw new IOException("Invalid transfer signature");
        if (prefix.get() != VERSION) throw new IOException("Unsupported transfer version");

        UUID id = new UUID(prefix.getLong(), prefix.getLong());
        int nameLength = Short.toUnsignedInt(prefix.getShort());
        if (nameLength == 0 || nameLength > MAX_NAME_BYTES) throw new IOException("Invalid file name length");
        ByteBuffer nameBuffer = ByteBuffer.allocate(nameLength);
        readFully(channel, nameBuffer);
        nameBuffer.flip();
        String fileName = StandardCharsets.UTF_8.newDecoder().decode(nameBuffer).toString();

        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        long fileSize = sizeBuffer.getLong();
        if (fileSize < 0 || fileSize > MAX_FILE_BYTES) {
            throw new IOException("File size must be between 0 and " + MAX_FILE_BYTES + " bytes");
        }
        return new TransferMetadata(id, fileName, fileSize);
    }

    static void readFully(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer);
            if (count < 0) throw new IOException("Connection ended before the transfer was complete");
        }
    }

    static void writeFully(WritableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
