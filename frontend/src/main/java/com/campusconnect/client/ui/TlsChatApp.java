package com.campusconnect.client.ui;

import com.campusconnect.security.tls.TlsClientSocketFactory;
import com.campusconnect.security.tls.TlsConnectionInfo;
import com.campusconnect.security.tls.TlsException;

import javax.net.ssl.SSLSocket;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Swing client application demonstrating Task 5 TLS secure chat connections
 * with real-time certificate verification and visual connection security status.
 */
public final class TlsChatApp {

    private final JFrame frame = new JFrame("Campus Connect | Secure TLS Chat");
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("8443", 6);
    private final JTextField truststoreField = new JTextField(findDefaultTruststore(), 22);
    private final JTextField usernameField = new JTextField("student-" + (int) (Math.random() * 900 + 100), 10);
    private final JButton browseButton = new JButton("Browse…");
    private final JButton connectButton = new JButton("Connect (TLS)");
    private final JButton untrustedButton = new JButton("Test Untrusted Reject");
    private final JButton disconnectButton = new JButton("Disconnect");

    // Security Status Banner
    private final JPanel securityPanel = new JPanel(new BorderLayout(8, 4));
    private final JLabel securityBadge = new JLabel("⚪ Not connected");
    private final JLabel securityDetails = new JLabel("Certificate not yet verified");

    // Chat display and input
    private final JTextArea chatArea = new JTextArea();
    private final JTextField messageField = new JTextField(35);
    private final JButton sendButton = new JButton("Send");

    // Active connection state
    private SSLSocket activeSocket;
    private PrintWriter socketWriter;
    private Thread listenerThread;

    public TlsChatApp() {
        buildWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new TlsChatApp().frame.setVisible(true);
        });
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(720, 560));
        frame.setLocationByPlatform(true);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("Campus Connect Secure Chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel("End-to-End TLS Encrypted TCP Communication");
        subtitle.setForeground(Color.GRAY);
        headerPanel.add(title, BorderLayout.NORTH);
        headerPanel.add(subtitle, BorderLayout.SOUTH);

        // Connection Config Form
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("TLS Connection Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Server Host:"), gbc);
        gbc.gridx = 1;
        configPanel.add(hostField, gbc);

        gbc.gridx = 2;
        configPanel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 3;
        configPanel.add(portField, gbc);

        gbc.gridx = 4;
        configPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 5;
        configPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        configPanel.add(new JLabel("Truststore:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(truststoreField, gbc);
        gbc.gridx = 5; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        configPanel.add(browseButton, gbc);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.add(connectButton);
        buttonRow.add(untrustedButton);
        buttonRow.add(disconnectButton);
        disconnectButton.setEnabled(false);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 6;
        configPanel.add(buttonRow, gbc);

        // Security Status Banner
        securityPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        securityPanel.setBackground(new Color(245, 245, 245));
        securityBadge.setFont(securityBadge.getFont().deriveFont(Font.BOLD, 13f));
        securityDetails.setFont(securityDetails.getFont().deriveFont(11f));
        securityPanel.add(securityBadge, BorderLayout.NORTH);
        securityPanel.add(securityDetails, BorderLayout.SOUTH);

        JPanel topContainer = new JPanel(new BorderLayout(0, 8));
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(configPanel, BorderLayout.CENTER);
        topContainer.add(securityPanel, BorderLayout.SOUTH);
        mainPanel.add(topContainer, BorderLayout.NORTH);

        // Chat Area
        chatArea.setEditable(false);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Encrypted Chat Messages (#general)"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Message input area
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        sendButton.setEnabled(false);
        messageField.setEnabled(false);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        // Event Listeners
        browseButton.addActionListener(e -> chooseTruststore());
        connectButton.addActionListener(e -> initiateConnection(false));
        untrustedButton.addActionListener(e -> initiateConnection(true));
        disconnectButton.addActionListener(e -> disconnect());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        frame.setContentPane(mainPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private void chooseTruststore() {
        JFileChooser chooser = new JFileChooser(new File("."));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            truststoreField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void initiateConnection(boolean simulateUntrusted) {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid port number.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path truststorePath;
        if (simulateUntrusted) {
            try {
                // Generate a temporary empty truststore to demonstrate rejection
                truststorePath = Files.createTempFile("empty-truststore-", ".p12");
                truststorePath.toFile().deleteOnExit();
                var ks = java.security.KeyStore.getInstance("PKCS12");
                ks.load(null, "changeit".toCharArray());
                try (var os = Files.newOutputStream(truststorePath)) {
                    ks.store(os, "changeit".toCharArray());
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Failed to create test truststore: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            String pathStr = truststoreField.getText().trim();
            truststorePath = Path.of(pathStr);
            if (!Files.exists(truststorePath)) {
                JOptionPane.showMessageDialog(frame, "Truststore file not found:\n" + truststorePath.toAbsolutePath(),
                        "Missing Truststore", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        setConnectingUi(true);
        new Thread(() -> doConnect(host, port, truststorePath)).start();
    }

    private void doConnect(String host, int port, Path truststorePath) {
        try {
            SSLSocket socket = TlsClientSocketFactory.connect(host, port, truststorePath, "changeit");
            TlsConnectionInfo info = TlsClientSocketFactory.getConnectionInfo(socket);

            this.activeSocket = socket;
            this.socketWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            SwingUtilities.invokeLater(() -> onConnected(info));

            // Start listening for incoming chat messages
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> appendChatMessage(msg));
            }
        } catch (TlsException e) {
            SwingUtilities.invokeLater(() -> onTlsFailure(e));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> onIoFailure(e));
        } finally {
            disconnect();
        }
    }

    private void onConnected(TlsConnectionInfo info) {
        securityPanel.setBackground(new Color(235, 248, 235));
        securityPanel.setBorder(BorderFactory.createLineBorder(new Color(76, 175, 80), 2));
        securityBadge.setText("🔒 " + info.getStatusBadge());
        securityBadge.setForeground(new Color(27, 94, 32));
        securityDetails.setText("Verified Peer: " + info.peerPrincipal() + " (CN: " + info.commonName() + ")");

        connectButton.setEnabled(false);
        untrustedButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        messageField.setEnabled(true);
        sendButton.setEnabled(true);

        appendChatMessage("[SYSTEM] Connected securely over TLS (" + info.protocol() + " · " + info.cipherSuite() + ")");
    }

    private void onTlsFailure(TlsException e) {
        securityPanel.setBackground(new Color(255, 235, 238));
        securityPanel.setBorder(BorderFactory.createLineBorder(new Color(229, 57, 53), 2));
        securityBadge.setText("❌ TLS Handshake Rejected (Untrusted / Insecure)");
        securityBadge.setForeground(new Color(183, 28, 28));
        securityDetails.setText(e.getMessage());

        appendChatMessage("[SECURITY ALERT] " + e.getMessage());
        JOptionPane.showMessageDialog(frame, e.getMessage(), "TLS Security Rejection", JOptionPane.WARNING_MESSAGE);
        setConnectingUi(false);
    }

    private void onIoFailure(IOException e) {
        securityPanel.setBackground(new Color(245, 245, 245));
        securityPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        securityBadge.setText("⚪ Connection Failed");
        securityBadge.setForeground(Color.DARK_GRAY);
        securityDetails.setText(e.getMessage());

        appendChatMessage("[NETWORK ERROR] " + e.getMessage());
        setConnectingUi(false);
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || socketWriter == null) return;

        String username = usernameField.getText().trim();
        String json = "{\"type\":\"CHAT\",\"room\":\"general\",\"username\":\"" + username + "\",\"text\":\""
                + text.replace("\"", "\\\"") + "\"}";

        socketWriter.println(json);
        appendChatMessage("[" + username + "] " + text);
        messageField.setText("");
    }

    private void appendChatMessage(String text) {
        chatArea.append(text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void disconnect() {
        try {
            if (activeSocket != null && !activeSocket.isClosed()) {
                activeSocket.close();
            }
        } catch (IOException ignored) {
        }
        activeSocket = null;
        socketWriter = null;

        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(true);
            untrustedButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            setConnectingUi(false);
        });
    }

    private void setConnectingUi(boolean connecting) {
        connectButton.setEnabled(!connecting);
        untrustedButton.setEnabled(!connecting);
        browseButton.setEnabled(!connecting);
        hostField.setEnabled(!connecting);
        portField.setEnabled(!connecting);
    }

    private static String findDefaultTruststore() {
        Path[] candidates = new Path[]{
                Path.of("certs", "server.truststore.p12"),
                Path.of("..", "certs", "server.truststore.p12")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p.toString();
        }
        return "certs/server.truststore.p12";
    }
}
