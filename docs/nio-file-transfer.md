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

## Run the demo

Build from the repository root:

```sh
mvn -f backend/pom.xml package
```

Start the server in one terminal. It stores successful transfers in
`received-files` under the current working directory:

```sh
java -cp backend/target/classes com.campusconnect.files.nio.NioFileTransferDemo server 0.0.0.0 5050
```

Send a file from another terminal/device:

```sh
java -cp backend/target/classes com.campusconnect.files.nio.NioFileTransferDemo client 127.0.0.1 5050 ./sample.pdf
```

For a LAN demo, replace `127.0.0.1` with the server computer's reachable IP and
allow the selected TCP port through the host firewall. Keep the demo on a
trusted network; authentication and encryption are provided by separate team
features and this component does not claim to provide either.

## Integration boundary

The client-side chat feature can call `NioFileTransferClient.send(path, host,
port)` after both users agree on a transfer. The receiving side can pass each
accepted `SocketChannel` to `NioFileTransferReceiver.receive(channel, folder)`.
The method returns the transfer ID, safe file name, and received size for UI
status. The reusable receiver performs one transfer per connection; the server
owner should dispatch accepted channels to the project's worker pool if the
application needs simultaneous transfers.

