package me.shedaniel.clothconfig2.injection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * A sleek, retro CMD-like console window that runs inside Java.
 * Redirects System.out and System.err to a scrollable black terminal area.
 */
public class DragoniteConsoleWindow extends JFrame {

    private final JTextArea consoleText;
    private PrintStream originalOut;
    private PrintStream originalErr;

    public DragoniteConsoleWindow() {
        setTitle("Dragonite Client Injector");
        setSize(640, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Dark terminal theme
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(12, 12, 12));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        consoleText = new JTextArea();
        consoleText.setEditable(false);
        consoleText.setBackground(new Color(12, 12, 12));
        consoleText.setForeground(new Color(204, 204, 204)); // Classic CMD light gray
        consoleText.setFont(new Font("Consolas", Font.PLAIN, 12));
        consoleText.setLineWrap(true);
        consoleText.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(consoleText);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(12, 12, 12));
        
        // Custom sleek scrollbars
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.getVerticalScrollBar().setBackground(new Color(12, 12, 12));

        panel.add(scrollPane, BorderLayout.CENTER);
        setContentPane(panel);

        // Intercept streams to write directly to our text area
        redirectSystemStreams();
    }

    private void redirectSystemStreams() {
        originalOut = System.out;
        originalErr = System.err;

        System.setOut(new PrintStream(new ConsoleOutputStream(false), true));
        System.setErr(new PrintStream(new ConsoleOutputStream(true), true));
    }

    public void restoreStreams() {
        if (originalOut != null) System.setOut(originalOut);
        if (originalErr != null) System.setErr(originalErr);
    }

    private class ConsoleOutputStream extends OutputStream {
        private final boolean isError;
        private final StringBuilder buffer = new StringBuilder();

        public ConsoleOutputStream(boolean isError) {
            this.isError = isError;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                String line = buffer.toString();
                buffer.setLength(0);
                
                // Write to original console log
                if (isError) {
                    originalErr.println(line);
                } else {
                    originalOut.println(line);
                }

                // Write to our Swing console JTextArea
                SwingUtilities.invokeLater(() -> {
                    consoleText.append(line + "\n");
                    consoleText.setCaretPosition(consoleText.getDocument().getLength());
                });
            } else if (b != '\r') {
                buffer.append((char) b);
            }
        }
    }
}
