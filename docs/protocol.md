# Shared network protocol

This is the team's initial contract. Agree on changes here before implementing them. Keep each TCP control/chat message on one UTF-8 JSON line (newline-delimited JSON); do not send passwords, tokens, or private data over UDP.

## TCP chat messages

| Type | Direction | Required fields | Purpose |
| --- | --- | --- | --- |
| `JOIN` | client to server | `room`, `username` | Join a chat room |
| `CHAT` | both directions | `room`, `username`, `text` | Send or receive a room message |
| `LEAVE` | client to server | `room`, `username` | Leave a room gracefully |
| `ERROR` | server to client | `code`, `message` | Report a rejected or malformed request |

The TCP server owns room membership and message broadcast. A client disconnect must remove that client from its rooms. Define the final JSON field names and validation rules together before the server and client implementations begin.

## UDP presence

The UDP presence feature is separate from chat. Keep datagrams small and non-sensitive. Use `PRESENCE` announcements and `PRESENCE_QUERY` requests with a username and timestamp; treat presence as best-effort and expire stale entries. Do not use UDP for reliable chat or file data.

## NIO file transfer

File transfer uses a TCP connection and NIO channels, not presence datagrams. Agree on transfer metadata (`transferId`, safe basename, byte length) before streaming bytes. Reject path traversal and enforce a documented maximum file size.

## TLS

TLS wraps the TCP connection between client and server. TLS must not change the application message format. Client certificate validation stays enabled; report handshake failures without exposing secrets.

## Ownership boundaries

- `server/tcp`: accept clients, worker pool, rooms, broadcast, disconnect cleanup.
- `client/tcp`: connection lifecycle and chat messages; coordinate the API with the server owner.
- `presence/udp`: announce/query/expire online status only.
- `files/nio`: metadata validation and streaming through NIO channels.
- `security/tls`: reusable TLS socket setup and certificates/truststore instructions.
- `common`: agreed message types and shared value objects only.
