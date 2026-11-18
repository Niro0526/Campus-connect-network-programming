package com.campusconnect.security.tls;

import java.io.IOException;

/**
 * Signals a TLS configuration, certificate verification, or handshake failure.
 * <p>
 * Messages in this exception are sanitized to ensure no secret credentials,
 * passwords, or sensitive system details are leaked.
 */
public class TlsException extends IOException {

    public TlsException(String message) {
        super(message);
    }

    public TlsException(String message, Throwable cause) {
        super(message, cause);
    }
}
