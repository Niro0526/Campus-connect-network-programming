# Task 4: NIO file transfer

This feature streams a file over TCP using Java NIO `SocketChannel`,
`ServerSocketChannel`, `FileChannel`, and bounded `ByteBuffer`s. It is a
standalone transfer component; the chat server and UDP presence service remain
separate.

## Wire format

Each connection carries one transfer:

| Field | Size | Meaning |
| --- | ---: | --- |
| Signature | 4 bytes | ASCII `CCFT` |
| Version | 1 byte | `1` |
| Transfer ID | 16 bytes | UUID, two big-endian 64-bit values |
| Name length | 2 bytes | Unsigned UTF-8 byte count |
| File name | 1–255 bytes | UTF-8 basename only |
| File size | 8 bytes | Non-negative byte count, big-endian |
| File content | File size bytes | Raw bytes streamed in 16 KiB buffers |

The receiver rejects invalid signatures, versions, unsafe names and files over
25 MiB. It writes into a transfer-specific `.part` file and only moves it to its
final name after receiving the declared byte count. Existing files are not
overwritten. A disconnect during transfer removes the partial file.

