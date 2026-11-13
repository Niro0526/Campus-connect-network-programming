# Campus Connect

A Java Network Programming group project demonstrating networking concepts from the semester through a campus chat and file-sharing application.

## Project structure

```text
backend/                 Java 17 Maven project
  src/main/java/com/campusconnect/
    common/              Shared message and protocol contracts
    server/tcp/           Concurrent TCP chat server
    client/tcp/           TCP client networking layer
    presence/udp/         UDP online-presence service
    files/nio/            NIO file transfer
    security/tls/         TLS socket configuration
  src/test/java/          Automated tests
frontend/                 Optional UI assets or client notes
docs/                     Protocol and setup documentation
.github/workflows/        CI build workflow
```

## Stack

- Java 17 and Maven
- TCP sockets and a thread pool for chat
- UDP datagrams for online presence only
- Java NIO channels for file transfer
- TLS sockets for secure TCP connections
- Optional JavaFX UI can be added separately from the networking layer

## Start

Install JDK 17 and Maven, then run `mvn -f backend/pom.xml verify`. Each feature owner should work in their assigned package and coordinate protocol changes through `docs/protocol.md`.

Task 4 NIO file-transfer protocol and demo: see [`docs/nio-file-transfer.md`](docs/nio-file-transfer.md).

## Team workflow

Use `develop` as the integration branch. Create a feature branch from `develop`, link your pull request to the matching GitHub issue, and merge into `develop` after review. Do not commit directly to `develop` after the scaffold is in place.
