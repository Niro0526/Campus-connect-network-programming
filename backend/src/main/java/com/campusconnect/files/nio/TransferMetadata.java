package com.campusconnect.files.nio;

import java.util.UUID;

/** Metadata sent before a file's bytes on the NIO transfer stream. */
public record TransferMetadata(UUID transferId, String fileName, long fileSize) {
}
