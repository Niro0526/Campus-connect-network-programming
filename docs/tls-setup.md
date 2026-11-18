# Task 5: TLS Secure Chat Connections

This document details the TLS (Transport Layer Security) encryption layer for Campus Connect TCP chat connections. It provides drop-in socket factory interfaces for both client and server, development certificate management, automated verification, and visual connection security indicators.

---

## 1. Architecture & Design

```
+-----------------------------------------------------------------------+
|                             Campus Connect                            |
|                                                                       |
|   +-----------------------+              +------------------------+   |
|   |   TCP Chat Server     |              |    TCP Chat Client     |   |
|   | (Room & Message Logic)|              |  (UI / Console Client) |   |
|   +-----------+-----------+              +------------+-----------+   |
|               |                                       |               |
|               | creates SSLServerSocket               | connects      |
|               v                                       v               |
|   +-----------------------+              +------------------------+   |
|   |TlsServerSocketFactory |              | TlsClientSocketFactory |   |
|   +-----------+-----------+              +------------+-----------+   |
|               |                                       |               |
|               +-------------------+-------------------+               |
|                                   |                                   |
|                                   v                                   |
|                       +-----------------------+                       |
|                       |   TlsContextFactory   |                       |
|                       +-----------+-----------+                       |
|                                   |                                   |
|                        loads PKCS12 keystores                         |
|                                   v                                   |
|                       [ certs/*.keystore.p12 ]                        |
|                       [ certs/*.truststore.p12]                       |
+-----------------------------------------------------------------------+
```

### Key Design Principles:
1. **Separation of Concerns:** TLS handshake and certificate validation are fully encapsulated within `com.campusconnect.security.tls`. Chat room logic, message framing, and command handling never need to deal with cryptographic handshake states.
2. **Standard Java Socket Abstraction:** `SSLServerSocket` extends `ServerSocket`, and `SSLSocket` extends `Socket`. Server and client handlers can use standard `InputStream` and `OutputStream`.
3. **Fail-Fast Handshake Validation:** Handshakes are triggered explicitly at connection establishment (`startHandshake()`). Handshake errors or untrusted certificates fail immediately with descriptive `TlsException` errors rather than failing mid-stream during message parsing.
4. **No Secrets Over UDP:** In accordance with the shared protocol contract, TLS protects TCP communication only. UDP presence announcements never contain secrets, tokens, or private keys.

---

## 2. Certificates & Keystores

All development certificates use modern **PKCS12** format with 2048-bit RSA keys and Subject Alternative Names (`SAN=dns:localhost,ip:127.0.0.1`) required for Java 17+ endpoint identification.

| File | Purpose | Contains | Default Password |
|---|---|---|---|
| `certs/server.keystore.p12` | Chat server keystore | Private key + Self-signed certificate (`CN=localhost`) | `changeit` |
| `certs/server.truststore.p12` | Client truststore | Public certificate of the trusted chat server | `changeit` |
| `certs/untrusted.keystore.p12` | Rogue/untrusted server keystore | Self-signed certificate (`CN=untrusted.example.com`) used to demonstrate client rejection | `changeit` |

### Regenerating Certificates

- **Windows (PowerShell):**
  ```powershell
  .\certs\generate-certs.ps1
  ```

- **Linux / macOS (Bash):**
  ```bash
  chmod +x certs/generate-certs.sh
  ./certs/generate-certs.sh
  ```

---

## 3. Integration Interface for Teammates

### For the TCP Server Owner (`com.campusconnect.server.tcp`)

Replace plain `new ServerSocket(port)` with `TlsServerSocketFactory`:

```java
import com.campusconnect.security.tls.TlsServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.nio.file.Path;

// Bind TLS ServerSocket enforcing TLS 1.2 and TLS 1.3
SSLServerSocket serverSocket = TlsServerSocketFactory.createServerSocket(
    "0.0.0.0", 8443, Path.of("certs/server.keystore.p12"), "changeit".toCharArray());

// Accept client with immediate handshake verification
SSLSocket clientSocket = TlsServerSocketFactory.acceptAndHandshake(serverSocket);

// Pass clientSocket (which extends java.net.Socket) to your client handler thread pool!
```

### For the TCP Client Owner (`com.campusconnect.client.tcp`)

Replace plain `new Socket(host, port)` with `TlsClientSocketFactory`:

```java
import com.campusconnect.security.tls.TlsClientSocketFactory;
import com.campusconnect.security.tls.TlsConnectionInfo;
import com.campusconnect.security.tls.TlsException;
import javax.net.ssl.SSLSocket;
import java.nio.file.Path;

try {
    SSLSocket socket = TlsClientSocketFactory.connect(
        "127.0.0.1", 8443, Path.of("certs/server.truststore.p12"), "changeit");

    // Retrieve verified TLS connection info for UI display
    TlsConnectionInfo info = TlsClientSocketFactory.getConnectionInfo(socket);
    System.out.println("Status: " + info.getStatusBadge()); 
    // Example: "🔒 Secure (TLSv1.3 · TLS_AES_256_GCM_SHA384)"

    // Use standard socket streams for JSON chat messages
    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

} catch (TlsException e) {
    // Clear user-facing message explaining rejection (e.g. untrusted certificate)
    System.err.println("Secure connection failed: " + e.getMessage());
}
```

### Displaying Connection Security Status in the UI

`TlsConnectionInfo` exposes the negotiated cryptographic parameters for JavaFX or Swing UI badges:

- `info.secure()`: `true` if end-to-end TLS is active.
- `info.protocol()`: Negotiated protocol (e.g. `TLSv1.3` or `TLSv1.2`).
- `info.cipherSuite()`: Active cipher suite (e.g. `TLS_AES_256_GCM_SHA384`).
- `info.peerPrincipal()`: Verified X.500 Distinguished Name of the server certificate.
- `info.commonName()`: Extracted CN (e.g. `localhost`).
- `info.getStatusBadge()`: Formatted string suitable for UI label, e.g. `"🔒 Secure (TLSv1.3 · TLS_AES_256_GCM_SHA384)"`.

---

## 4. How to Run the Demonstrations

### Step 1: Build the Project
From the repository root:
```bash
mvn -B clean package
```

---

### Demo A: Two-Terminal CLI Demo

**Terminal 1 — Start the TLS Chat Server:**
```bash
java -cp backend/target/classes com.campusconnect.security.tls.TlsChatDemo server 8443
```
*Output:*
```text
==================================================
   Campus Connect - Secure TLS Chat Server
==================================================
Binding to port: 8443
Loading server keystore: .../certs/server.keystore.p12
[READY] TLS Chat Server listening on 0.0.0.0:8443
Waiting for secure client connections...
```

**Terminal 2 — Connect with Trusted TLS Client:**
```bash
java -cp backend/target/classes com.campusconnect.security.tls.TlsChatDemo client 127.0.0.1 8443
```
*Output:*
```text
[HANDSHAKE SUCCESSFUL]
Status: 🔒 Secure (TLSv1.3 · TLS_AES_256_GCM_SHA384)
Negotiated Protocol: TLSv1.3
Cipher Suite: TLS_AES_256_GCM_SHA384
Verified Server Identity: CN=localhost, OU=CampusConnect, O=CampusConnect, L=Campus, ST=State, C=US
Common Name: localhost
--------------------------------------------------
Type a chat message and press Enter (or 'quit' to exit):
```

Type a message:
```text
Hello TLS Server!
[SERVER ACK] {"type":"CHAT_ACK","received":"{\"type\":\"CHAT\",\"room\":\"general\",\"username\":\"student-456\",\"text\":\"Hello TLS Server!\"}"}
```

---

### Demo B: Graphical UI Demo (Swing / Desktop Client)

Leave the TLS chat server running in Terminal 1, and launch the graphical chat client:

```bash
java -cp "frontend/target/classes;backend/target/classes" com.campusconnect.client.ui.TlsChatApp
```

1. Click **Connect (TLS)**:
   - Green security banner appears with lock icon: `🔒 🔒 Secure (TLSv1.3 · TLS_AES_256_GCM_SHA384)`.
   - Verified Peer: `CN=localhost, OU=CampusConnect...`.
   - Type chat messages and see bidirectional encrypted chat.
2. Click **Disconnect**, then click **Test Untrusted Reject**:
   - The client attempts a TLS connection with an untrusted truststore.
   - Handshake fails immediately and safely.
   - Red security banner appears: `❌ TLS Handshake Rejected (Untrusted / Insecure)`.
   - Dialog box informs user: *"TLS handshake rejected: Untrusted server certificate from '127.0.0.1'. The certificate was not signed by a trusted CA or is missing from the client truststore."*

---

### Demo C: Reject Untrusted Server Certificate (CLI)

With the server running, test untrusted certificate rejection via CLI:

```bash
java -cp backend/target/classes com.campusconnect.security.tls.TlsChatDemo untrusted 127.0.0.1 8443
```
*Output:*
```text
==================================================
   Simulating Untrusted / Rogue Server Test
==================================================
Attempting TLS connection without trusting the server certificate...

[TEST PASSED] The client safely rejected the untrusted certificate:
>> TLS handshake rejected: Untrusted server certificate from '127.0.0.1'. The certificate was not signed by a trusted CA or is missing from the client truststore.
```

---

## 5. Automated Tests

The test suite runs automatically during `mvn test` or `mvn verify`:

```bash
mvn -B test -Dtest=TlsHandshakeTest
```

### Verified Test Cases:
1. `testTrustedHandshakeAndMessageExchange`:
   - Binds ephemeral server socket with development keystore.
   - Client connects with matching truststore.
   - Completes handshake and verifies protocol version (TLSv1.2 or TLSv1.3) and cipher suite.
   - Transmits and echoes a JSON chat payload over the encrypted socket stream.
2. `testUntrustedServerCertificateRejected`:
   - Spins up server with untrusted certificate.
   - Client connects using trusted client truststore.
   - Asserts `TlsException` is thrown with descriptive error message and no password leaks.
3. `testInvalidPasswordThrowsClearError`:
   - Asserts that attempting to unlock keystore with wrong password throws sanitized error.
4. `testMissingKeystoreThrowsClearError`:
   - Asserts that missing file throws explicit missing file exception.
