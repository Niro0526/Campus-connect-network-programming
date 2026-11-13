package com.campusconnect.client.ui;

import com.campusconnect.files.nio.NioFileTransferClient;
import com.campusconnect.files.nio.TransferMetadata;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.concurrent.ExecutionException;

/** Small Swing client UI for the Task 4 NIO file-transfer demonstration. */
public final class NioFileTransferApp {
    private final JFrame frame = new JFrame("Campus Connect | NIO File Transfer");
    private final JTextField hostField = new JTextField("127.0.0.1", 18);
    private final JTextField portField = new JTextField("5050", 8);
    private final JTextField fileField = new JTextField(28);
    private final JButton chooseButton = new JButton("Choose file");
    private final JButton sendButton = new JButton("Send file");
    private final JLabel statusLabel = new JLabel("Choose a file, then connect to a running receiver.");
    private final JProgressBar progressBar = new JProgressBar();
    private File selectedFile;

    private NioFileTransferApp() {
        buildWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Swing's default look and feel is sufficient if the system one is unavailable.
            }
            new NioFileTransferApp().frame.setVisible(true);
        });
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(560, 310));
        frame.setLocationByPlatform(true);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JLabel heading = new JLabel("Send a file over the network");
        heading.setFont(heading.getFont().deriveFont(20f));
        JLabel subheading = new JLabel("Campus Connect · Java NIO transfer demo");
        JPanel titlePanel = new JPanel(new BorderLayout(0, 5));
        titlePanel.add(heading, BorderLayout.NORTH);
        titlePanel.add(subheading, BorderLayout.SOUTH);
        content.add(titlePanel, BorderLayout.NORTH);

        fileField.setEditable(false);
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filePanel.add(fileField);
        filePanel.add(chooseButton);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 0, 8, 10);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        fields.add(new JLabel("Receiver host"), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        fields.add(hostField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        fields.add(new JLabel("TCP port"), constraints);
        constraints.gridx = 1;
        fields.add(portField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        fields.add(new JLabel("File"), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        fields.add(filePanel, constraints);
        content.add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionPanel.add(sendButton);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(progressBar, BorderLayout.NORTH);
        footer.add(actionPanel, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        chooseButton.addActionListener(event -> chooseFile());
        sendButton.addActionListener(event -> sendFile());
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            fileField.setText(selectedFile.getAbsolutePath());
            statusLabel.setText("Ready to send " + selectedFile.getName());
        }
    }

    private void sendFile() {
        if (selectedFile == null || !selectedFile.isFile()) {
            JOptionPane.showMessageDialog(frame, "Choose an existing file first.",
                    "No file selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65_535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(frame, "Enter a TCP port between 1 and 65535.",
                    "Invalid port", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Enter the receiver's host name or IP address.",
                    "Invalid host", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setBusy(true, "Connecting and sending " + selectedFile.getName() + "…");
        File fileToSend = selectedFile;
        new SwingWorker<TransferMetadata, Void>() {
            @Override
            protected TransferMetadata doInBackground() throws Exception {
                return NioFileTransferClient.send(fileToSend.toPath(), host, port);
            }

            @Override
            protected void done() {
                try {
                    TransferMetadata result = get();
                    setBusy(false, "Sent " + result.fileName() + " (" + result.fileSize()
                            + " bytes). Transfer ID: " + result.transferId());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    setBusy(false, "Transfer was interrupted.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    setBusy(false, "Transfer failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        chooseButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        progressBar.setVisible(busy);
        progressBar.setIndeterminate(busy);
    }
}
