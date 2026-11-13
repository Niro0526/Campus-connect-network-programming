package com.campusconnect.files.nio;

import java.nio.file.Path;

/** CLI entry point for a two-terminal NIO file-transfer demonstration. */
public final class NioFileTransferDemo {
    private NioFileTransferDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equalsIgnoreCase("server")) {
            NioFileTransferServer.serve(args[1], Integer.parseInt(args[2]), Path.of("received-files"));
            return;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("client")) {
            TransferMetadata sent = NioFileTransferClient.send(
                    Path.of(args[3]), args[1], Integer.parseInt(args[2]));
            System.out.println("Sent " + sent.fileName() + " (" + sent.fileSize()
                    + " bytes, transfer " + sent.transferId() + ")");
            return;
        }
        System.out.println("Usage:");
        System.out.println("  NioFileTransferDemo server <bind-host> <port>");
        System.out.println("  NioFileTransferDemo client <server-host> <port> <file-path>");
    }
}
