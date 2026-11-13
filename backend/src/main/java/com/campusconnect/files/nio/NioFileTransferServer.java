package com.campusconnect.files.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

/** Small blocking-channel server for the standalone NIO transfer demonstration. */
public final class NioFileTransferServer {
    private NioFileTransferServer() { }

    public static void serve(String bindHost, int port, Path destinationDirectory) throws IOException {
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.configureBlocking(true);
            listener.bind(new InetSocketAddress(bindHost, port));
            System.out.println("NIO file receiver listening at " + listener.getLocalAddress());
            while (!Thread.currentThread().isInterrupted()) {
                try (SocketChannel client = listener.accept()) {
                    client.configureBlocking(true);
                    TransferMetadata metadata = NioFileTransferReceiver.receive(client, destinationDirectory);
                    System.out.println("Received " + metadata.fileName() + " (" + metadata.fileSize()
                            + " bytes, transfer " + metadata.transferId() + ")");
                } catch (IOException exception) {
                    System.err.println("Transfer rejected: " + exception.getMessage());
                }
            }
        }
    }
}
