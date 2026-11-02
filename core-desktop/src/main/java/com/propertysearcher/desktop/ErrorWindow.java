package com.propertysearcher.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ErrorWindow extends JDialog {

    public ErrorWindow(Frame owner, String message) {
        super(owner, "Error", true);
        setSize(360, 170);
        setLocationRelativeTo(owner);
        setResizable(false);

        Color bg = BotGUI.COLOR_BG;
        Color text = Color.BLACK;
        Font font = BotGUI.getAppFont(Font.PLAIN, 13);

        getContentPane().setBackground(bg);

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBackground(bg);
        mainPanel.setBorder(new EmptyBorder(18, 20, 14, 20));

        // Create a label to display the error message
        JLabel messageLabel = new JLabel("<html><center>" + message + "</center></html>", SwingConstants.CENTER);
        messageLabel.setFont(font);
        messageLabel.setForeground(text);

        // Create an OK button to close the dialog matching theme
        JButton okButton = BotGUI.createButton("OK", 0, 0, 110, 36);
        okButton.setPreferredSize(new Dimension(110, 36));
        okButton.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnPanel.setBackground(bg);
        btnPanel.add(okButton);

        mainPanel.add(messageLabel, BorderLayout.CENTER);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    // Static method for convenience
    public static void showErrorWindow(Frame owner, String message) {
        ErrorWindow errorWindow = new ErrorWindow(owner, message);
        errorWindow.setVisible(true);
    }
}
