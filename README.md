# Campus Connect

A Java Network Programming group project demonstrating networking concepts from the semester through a campus chat and file-sharing application.

## Project structure

```text
Campus-connect-network-programming/
├── pom.xml                               Maven parent and reactor build
├── backend/                              Java 17 networking module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/campusconnect/
│       │   ├── common/                   Shared message and protocol contracts
│       │   ├── server/tcp/               Concurrent TCP chat server
│       │   ├── client/tcp/               TCP client networking layer
│       │   ├── presence/udp/             UDP presence service
│       │   ├── files/nio/                NIO transfer protocol and client/server
│       │   └── security/tls/              TLS socket configuration
│       └── test/java/                    Automated tests
├── frontend/                             Separate Java Swing UI module
│   ├── pom.xml                            Depends on campus-connect-backend
│   └── src/main/java/com/campusconnect/client/ui/
│       └── NioFileTransferApp.java         NIO file-sender window
├── docs/                                 Protocol and setup documentation
├── .github/workflows/                    CI build workflow
├── .gitignore
└── README.md
```

The frontend and backend are separate Maven modules. The frontend depends on the
backend module's NIO client; the parent POM builds them in dependency order.

## Stack

- Java 17 and Maven
- TCP sockets and a thread pool for chat
- UDP datagrams for online presence only
- Java NIO channels for file transfer
- TLS sockets for secure TCP connections
- Optional Swing UI for the NIO file sender, separate from the networking layer

## Start

Install JDK 17 and Maven, then run `mvn -B verify` from the repository root to build both modules. Each feature owner should work in their assigned package and coordinate protocol changes through `docs/protocol.md`.

Task 4 NIO file-transfer protocol and demo: see [`docs/nio-file-transfer.md`](docs/nio-file-transfer.md).

## Team workflow

Use `develop` as the integration branch. Create a feature branch from `develop`, link your pull request to the matching GitHub issue, and merge into `develop` after review. Do not commit directly to `develop` after the scaffold is in place.
