package PropertySeacher;

import javax.swing.*;
import java.awt.*;

public class ErrorWindow extends JDialog {

    public ErrorWindow(Frame owner, String message) {
        super(owner, "Error", true);
        setSize(300, 150);
        setLocationRelativeTo(owner);
        setResizable(true);
        setLayout(new BorderLayout());

        // Set the background color and font
        getContentPane().setBackground(new Color(182, 234, 233));
        Font font = new Font("Comic Sans MS", Font.PLAIN, 12);

        // Create a label to display the error message
        JLabel messageLabel = new JLabel(message, SwingConstants.CENTER);
        messageLabel.setFont(font);
        messageLabel.setForeground(Color.BLACK);

        // Create an OK button to close the dialog
        JButton okButton = new JButton("OK");
        okButton.setFont(font);
        okButton.addActionListener(e -> dispose());

        // Add components to the dialog
        add(messageLabel, BorderLayout.CENTER);
        add(okButton, BorderLayout.SOUTH);
    }

    // Static method for convenience
    public static void showErrorWindow(Frame owner, String message) {
        ErrorWindow errorWindow = new ErrorWindow(owner, message);
        errorWindow.setVisible(true);
    }
}
