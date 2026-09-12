package me.shedaniel.clothconfig2.injection;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import me.shedaniel.clothconfig2.internal.StandaloneLicenseCoordinator;

public class DragoniteLoader extends JFrame {

    private static final String[] DIALOG_HANDLER_CLASS_NAMES = {
            "me.shedaniel.clothconfig2.internal.DialogHandler",
            "com.dragonite.client.DialogHandler"
    };

    private static final Color COLOR_BG = new Color(13, 15, 20);
    private static final Color COLOR_PANEL = new Color(24, 27, 36);
    private static final Color COLOR_PANEL_BORDER = new Color(255, 255, 255, 14);
    private static final Color COLOR_ACCENT = new Color(0, 198, 255);
    private static final Color COLOR_TEXT = new Color(232, 236, 244);
    private static final Color COLOR_TEXT_MUTED = new Color(122, 132, 158);
    private static final Color COLOR_OK = new Color(72, 220, 130);
    private static final Color COLOR_ERROR = new Color(255, 90, 90);

    private static final String FONT_UI = "Segoe UI";
    private static final String FONT_MONO = "Consolas";

    private Point dragOffset;
    private JPanel mainContentCard;
    private CardLayout cardLayout;

    // License screen
    private JTextField keyField;
    private JLabel licenseErrorLabel;
    private JButton activateButton;

    // Inject screen
    private StatusPill statusPill;
    private JLabel statusHintLabel;
    private JButton injectButton;
    private ThinProgressBar progressBar;
    private CompletionCard completionCard;
    private Timer mcDetectTimer;
    private Timer closeCountdownTimer;

    private final AtomicBoolean licenseCheckInFlight = new AtomicBoolean(false);
    private final boolean injectOnly;

    private BufferedImage imgHeaderLogo;

    public DragoniteLoader() {
        this(false);
    }

    /** Inject step only — license must already be authenticated via {@link StandaloneLicenseCoordinator}. */
    public static DragoniteLoader forAuthorizedInject() {
        return new DragoniteLoader(true);
    }

    private final java.util.concurrent.atomic.AtomicBoolean hwidDeferInFlight =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private DragoniteLoader(boolean injectOnly) {
        this.injectOnly = injectOnly;
        try {
            InputStream isHeader = getClass().getResourceAsStream(
                    "/me/shedaniel/clothconfig2/impl/res/headertext.png");
            if (isHeader != null) {
                imgHeaderLogo = ImageIO.read(isHeader);
            }
        } catch (Exception e) {
            System.err.println("Could not load image resources: " + e.getMessage());
        }

        setUndecorated(true);
        setTitle("Dragonite Client");
        setSize(injectOnly ? 440 : 400, injectOnly ? 390 : 340);
        setLocationRelativeTo(null);
        setBackground(new Color(0, 0, 0, 0));

        JPanel basePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_BG);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 18, 18));
                g2.setColor(COLOR_PANEL_BORDER);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 17, 17));
                g2.dispose();
            }
        };
        basePanel.setLayout(new BorderLayout());
        basePanel.setBorder(new EmptyBorder(10, 14, 18, 14));

        // Whole window is draggable (no title text — the wordmark below is the brand)
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
        };
        MouseMotionAdapter dragMotion = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point curr = getLocation();
                setLocation(curr.x + e.getX() - dragOffset.x, curr.y + e.getY() - dragOffset.y);
            }
        };
        basePanel.addMouseListener(dragHandler);
        basePanel.addMouseMotionListener(dragMotion);

        // Title bar: window controls only
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        titleBar.setOpaque(false);
        JButton btnMin = createTitleBtn("–");
        btnMin.addActionListener(e -> setState(Frame.ICONIFIED));
        JButton btnClose = createTitleBtn("✕");
        btnClose.addActionListener(e -> System.exit(0));
        titleBar.add(btnMin);
        titleBar.add(btnClose);
        basePanel.add(titleBar, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        mainContentCard = new JPanel(cardLayout);
        mainContentCard.setOpaque(false);

        if (!injectOnly) {
            mainContentCard.add(createLicenseScreen(), "LICENSE");
        }
        mainContentCard.add(createInjectScreen(), "INJECT");

        basePanel.add(mainContentCard, BorderLayout.CENTER);
        setContentPane(basePanel);

        if (injectOnly) {
            cardLayout.show(mainContentCard, "INJECT");
            startMinecraftDetection();
            return;
        }

        String saved = invokeLoadSavedLicense();
        if (saved != null && !saved.isEmpty()) {
            keyField.setText(saved);
            SwingUtilities.invokeLater(this::validateLicenseKey);
        }
    }

    // ── Shared brand header (shown once per screen, never duplicated) ────────
    private JComponent createBrandHeader(int logoWidth, int logoHeight) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JComponent brand;
        if (imgHeaderLogo != null) {
            ImageIcon logoIcon = new ImageIcon(
                    imgHeaderLogo.getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH));
            brand = new JLabel(logoIcon);
        } else {
            JLabel wordmark = new JLabel("DRAGONITE");
            wordmark.setFont(new Font(FONT_UI, Font.BOLD, 26));
            wordmark.setForeground(COLOR_TEXT);
            brand = wordmark;
        }
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(brand);
        return header;
    }

    private JButton createTitleBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(FONT_UI, Font.PLAIN, 12));
        btn.setForeground(COLOR_TEXT_MUTED);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(0, 6, 0, 6));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { btn.setForeground(COLOR_TEXT); }
            @Override
            public void mouseExited(MouseEvent e) { btn.setForeground(COLOR_TEXT_MUTED); }
        });
        return btn;
    }

    /** Flat rounded accent button with hover / press / disabled states. */
    private JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(255, 255, 255, 18));
                } else if (getModel().isPressed()) {
                    g2.setColor(COLOR_ACCENT.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(COLOR_ACCENT.brighter());
                } else {
                    g2.setColor(COLOR_ACCENT);
                }
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(isEnabled() ? COLOR_BG : COLOR_TEXT_MUTED);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(new Font(FONT_UI, Font.BOLD, 12));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Rounded status chip: colored dot + single line of text. */
    private static final class StatusPill extends JComponent {
        private Color dotColor = new Color(255, 190, 70);
        private String text = "";

        void setState(Color dot, String value) {
            this.dotColor = dot;
            this.text = value;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(330, 42);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(COLOR_PANEL);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
            g2.setColor(COLOR_PANEL_BORDER);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 11, 11));

            int dotSize = 9;
            int dotX = 16;
            int dotY = (getHeight() - dotSize) / 2;
            g2.setColor(dotColor);
            g2.fillOval(dotX, dotY, dotSize, dotSize);

            g2.setColor(COLOR_TEXT);
            g2.setFont(new Font(FONT_UI, Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            String shown = text;
            while (fm.stringWidth(shown) > getWidth() - dotX - dotSize - 24 && shown.length() > 4) {
                shown = shown.substring(0, shown.length() - 2);
            }
            if (!shown.equals(text)) {
                shown = shown.trim() + "…";
            }
            g2.drawString(shown, dotX + dotSize + 8,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Thin rounded progress track, painted manually (no native LAF artifacts). */
    private static final class ThinProgressBar extends JComponent {
        private boolean running;
        private int progress;
        private String phase = "Ready";
        private final Timer animator;

        ThinProgressBar() {
            animator = new Timer(80, e -> {
                if (running && progress < 90) {
                    progress++;
                    repaint();
                }
            });
        }

        void start(String value) {
            running = true;
            progress = 16;
            phase = value;
            animator.start();
            repaint();
        }

        void setPhase(String value, int minimumProgress) {
            phase = value;
            progress = Math.max(progress, Math.min(90, minimumProgress));
            repaint();
        }

        void complete() {
            animator.stop();
            running = false;
            progress = 100;
            phase = "Verified completion";
            repaint();
        }

        void stop() {
            animator.stop();
            running = false;
            progress = 0;
            phase = "Ready";
            repaint();
        }

        boolean isRunning() {
            return running;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(330, 34);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(new Font(FONT_UI, Font.PLAIN, 10));
            g2.setColor(COLOR_TEXT_MUTED);
            g2.drawString(phase, 0, 11);
            String percent = progress + "%";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(percent, getWidth() - fm.stringWidth(percent), 11);

            int trackY = 23;
            int trackHeight = 5;
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fill(new RoundRectangle2D.Double(0, trackY, getWidth(), trackHeight, 5, 5));
            if (progress > 0) {
                int width = Math.max(trackHeight, (int) (getWidth() * (progress / 100.0)));
                g2.setColor(COLOR_ACCENT);
                g2.fill(new RoundRectangle2D.Double(0, trackY, width, trackHeight, 5, 5));
            }
            g2.dispose();
        }
    }

    /** Verified completion summary shown only after the protected action returns success. */
    private static final class CompletionCard extends JComponent {
        private String completedAt = "";
        private String countdown = "";

        void showCompletion(String time, int seconds) {
            completedAt = "Completed at " + time;
            setCountdown(seconds);
            setVisible(true);
            repaint();
        }

        void setCountdown(int seconds) {
            countdown = "Closing automatically in " + seconds
                    + (seconds == 1 ? " second" : " seconds");
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(330, 76);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(21, 48, 37, 150));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
            g2.setColor(new Color(COLOR_OK.getRed(), COLOR_OK.getGreen(), COLOR_OK.getBlue(), 75));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5,
                    getWidth() - 1, getHeight() - 1, 11, 11));

            g2.setColor(COLOR_OK);
            g2.fillOval(16, 18, 22, 22);
            g2.setColor(COLOR_BG);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(22, 29, 27, 34);
            g2.drawLine(27, 34, 34, 25);

            g2.setColor(COLOR_OK);
            g2.setFont(new Font(FONT_UI, Font.BOLD, 13));
            g2.drawString("Injection complete", 50, 26);
            g2.setColor(COLOR_TEXT_MUTED);
            g2.setFont(new Font(FONT_UI, Font.PLAIN, 10));
            g2.drawString(completedAt, 50, 44);
            g2.drawString(countdown, 50, 60);
            g2.dispose();
        }
    }

    // ── SCREEN 1: License ─────────────────────────────────────────────────────
    private JPanel createLicenseScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 24, 0, 24));

        panel.add(createBrandHeader(240, 48));
        panel.add(Box.createVerticalStrut(18));

        JLabel infoLabel = new JLabel("Enter your license key");
        infoLabel.setFont(new Font(FONT_UI, Font.PLAIN, 13));
        infoLabel.setForeground(COLOR_TEXT_MUTED);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(infoLabel);

        panel.add(Box.createVerticalStrut(12));

        keyField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_PANEL);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(isFocusOwner() ? COLOR_ACCENT : COLOR_PANEL_BORDER);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 9, 9));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        keyField.setCaretColor(COLOR_ACCENT);
        keyField.setForeground(COLOR_TEXT);
        keyField.setFont(new Font(FONT_MONO, Font.PLAIN, 13));
        keyField.setOpaque(false);
        keyField.setBorder(new EmptyBorder(9, 14, 9, 14));
        keyField.setPreferredSize(new Dimension(300, 38));
        keyField.setMaximumSize(new Dimension(300, 38));
        keyField.setAlignmentX(Component.CENTER_ALIGNMENT);
        keyField.setHorizontalAlignment(JTextField.CENTER);
        keyField.addActionListener(e -> validateLicenseKey());
        panel.add(keyField);

        panel.add(Box.createVerticalStrut(8));
        licenseErrorLabel = new JLabel(" ");
        licenseErrorLabel.setFont(new Font(FONT_UI, Font.PLAIN, 11));
        licenseErrorLabel.setForeground(COLOR_ERROR);
        licenseErrorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(licenseErrorLabel);

        panel.add(Box.createVerticalStrut(10));

        activateButton = createPrimaryButton("ACTIVATE");
        activateButton.setPreferredSize(new Dimension(300, 40));
        activateButton.setMaximumSize(new Dimension(300, 40));
        activateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        activateButton.addActionListener(e -> validateLicenseKey());
        panel.add(activateButton);

        return panel;
    }

    private void validateLicenseKey() {
        if (!licenseCheckInFlight.compareAndSet(false, true)) {
            return;
        }
        String key = keyField.getText().trim();
        if (key.isEmpty()) {
            licenseCheckInFlight.set(false);
            licenseErrorLabel.setForeground(COLOR_ERROR);
            licenseErrorLabel.setText("Please enter a key");
            return;
        }

        licenseErrorLabel.setForeground(COLOR_ACCENT);
        licenseErrorLabel.setText("Verifying license…");
        keyField.setEnabled(false);
        activateButton.setEnabled(false);

        new Thread(() -> {
            try {
                StandaloneLicenseCoordinator.AuthorizationOutcome outcome =
                        StandaloneLicenseCoordinator.authenticate(key);

                SwingUtilities.invokeLater(() -> {
                    licenseCheckInFlight.set(false);
                    keyField.setEnabled(true);
                    activateButton.setEnabled(true);
                    if (outcome.permitsUiTransition()) {
                        licenseErrorLabel.setText(" ");
                        cardLayout.show(mainContentCard, "INJECT");
                        startMinecraftDetection();
                    } else {
                        licenseErrorLabel.setForeground(COLOR_ERROR);
                        if (outcome.blacklisted()) {
                            licenseErrorLabel.setText("License or machine is blacklisted");
                        } else {
                            String err = outcome.message() != null
                                    ? outcome.message() : "Invalid key";
                            if (outcome.attemptsRemaining() != null) {
                                err += " (" + outcome.attemptsRemaining() + " attempts left)";
                            }
                            licenseErrorLabel.setText(err);
                        }
                    }
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> {
                    licenseCheckInFlight.set(false);
                    keyField.setEnabled(true);
                    activateButton.setEnabled(true);
                    licenseErrorLabel.setForeground(COLOR_ERROR);
                    licenseErrorLabel.setText("Unexpected security check error");
                });
            }
        }, "Dragonite-License-Check").start();
    }

    // ── SCREEN 2: Inject ──────────────────────────────────────────────────────
    private JPanel createInjectScreen() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 24, 0, 24));

        panel.add(createBrandHeader(240, 48));
        panel.add(Box.createVerticalStrut(24));

        statusPill = new StatusPill();
        statusPill.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusPill.setState(new Color(255, 190, 70), "Scanning for Minecraft…");
        panel.add(statusPill);

        panel.add(Box.createVerticalStrut(10));

        statusHintLabel = new JLabel("Launch the game, then inject");
        statusHintLabel.setFont(new Font(FONT_UI, Font.PLAIN, 11));
        statusHintLabel.setForeground(COLOR_TEXT_MUTED);
        statusHintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(statusHintLabel);

        panel.add(Box.createVerticalStrut(15));

        progressBar = new ThinProgressBar();
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(progressBar);

        panel.add(Box.createVerticalStrut(14));

        injectButton = createPrimaryButton("INJECT");
        injectButton.setPreferredSize(new Dimension(330, 44));
        injectButton.setMaximumSize(new Dimension(330, 44));
        injectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        injectButton.setEnabled(false);
        injectButton.addActionListener(e -> startInjection());
        panel.add(injectButton);

        panel.add(Box.createVerticalStrut(12));

        completionCard = new CompletionCard();
        completionCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        completionCard.setVisible(false);
        panel.add(completionCard);

        return panel;
    }

    private void startMinecraftDetection() {
        if (mcDetectTimer != null) {
            mcDetectTimer.stop();
        }
        mcDetectTimer = new Timer(1500, e -> refreshMinecraftDetection());
        mcDetectTimer.start();
        refreshMinecraftDetection();
    }

    private void refreshMinecraftDetection() {
        if (injectButton == null || statusPill == null || progressBar.isRunning()) {
            return;
        }
        java.util.Optional<String> pid = AgentAttacher.findMinecraftProcess();
        if (pid.isPresent()) {
            statusPill.setState(COLOR_OK, "Minecraft detected · PID " + pid.get());
            injectButton.setEnabled(true);
            statusHintLabel.setText("Ready — inject once you are in-game");
            progressBar.stop();
            maybeTryDeferredHwidAuth();
        } else {
            statusPill.setState(new Color(255, 190, 70), "Waiting for Minecraft…");
            injectButton.setEnabled(false);
            statusHintLabel.setText("Launch the game, then inject");
            progressBar.stop();
        }
    }

    private void maybeTryDeferredHwidAuth() {
        if (hwidDeferInFlight.get()) {
            return;
        }
        if (me.shedaniel.clothconfig2.internal.SessionHandler.getInstance().isAuthenticated()) {
            return;
        }
        if (!hwidDeferInFlight.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            try {
                boolean ok = StandaloneLicenseCoordinator.tryHwidAutoLoginWhenGameRunning();
                if (ok) {
                    SwingUtilities.invokeLater(() -> {
                        if (licenseErrorLabel != null) {
                            licenseErrorLabel.setForeground(COLOR_OK);
                            licenseErrorLabel.setText("HWID login OK — ready to inject");
                        }
                        cardLayout.show(mainContentCard, "INJECT");
                    });
                }
            } finally {
                hwidDeferInFlight.set(false);
            }
        }, "Dragonite-Deferred-Hwid").start();
    }

    private void startInjection() {
        String licenseKey = injectOnly
                ? invokeLoadSavedLicense()
                : keyField.getText().trim();
        injectButton.setEnabled(false);
        if (mcDetectTimer != null) {
            mcDetectTimer.stop();
        }
        completionCard.setVisible(false);
        progressBar.start("Verifying authorization");
        statusPill.setState(COLOR_ACCENT, "Verifying authorization…");
        statusHintLabel.setText("Checking your session and protected build");

        new Thread(() -> {
            try {
            StandaloneLicenseCoordinator.AuthorizationOutcome recheck =
                    StandaloneLicenseCoordinator.ensureAuthorizedForInject(
                            licenseKey != null ? licenseKey : "");
            if (!recheck.permitsUiTransition()) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.stop();
                    statusPill.setState(COLOR_ERROR, "Authorization rejected");
                    statusHintLabel.setText(recheck.message() != null
                            ? recheck.message()
                            : "Wait until you are in-game and try again");
                    injectButton.setEnabled(true);
                    if (!injectOnly) {
                        cardLayout.show(mainContentCard, "LICENSE");
                    }
                });
                return;
            }

            SwingUtilities.invokeLater(() ->
            {
                statusPill.setState(COLOR_ACCENT, "Security checks passed");
                statusHintLabel.setText("Keep Minecraft open while Dragonite finishes");
                progressBar.setPhase("Attaching protected runtime", 38);
            });
            boolean success = Main.executeInjection();
            boolean verifiedSuccess = success
                    && me.shedaniel.clothconfig2.internal.ProtectedActionGate
                    .allowExistingInjectionRequest();
            SwingUtilities.invokeLater(() -> {
                if (verifiedSuccess) {
                    progressBar.complete();
                    statusPill.setState(COLOR_OK, "Injection complete");
                    statusHintLabel.setText("Protected runtime and authorization verified");
                    injectButton.setVisible(false);
                    String completedAt = LocalTime.now().format(
                            DateTimeFormatter.ofPattern("HH:mm:ss"));
                    completionCard.showCompletion(completedAt, 5);
                    panelAfterStateChange();
                    startCloseCountdown();
                } else {
                    progressBar.stop();
                    statusPill.setState(COLOR_ERROR, "Injection failed");
                    statusHintLabel.setText(success
                            ? "Final authorization verification failed"
                            : "Check the game log for details");
                    injectButton.setEnabled(true);
                }
            });
            } catch (Throwable error) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.stop();
                    statusPill.setState(COLOR_ERROR, "Authorization check failed");
                    statusHintLabel.setText("Security service returned an unexpected error");
                    injectButton.setEnabled(true);
                });
            }
        }, "Dragonite-Standalone-Inject").start();
    }

    private void panelAfterStateChange() {
        mainContentCard.revalidate();
        mainContentCard.repaint();
    }

    private void startCloseCountdown() {
        if (closeCountdownTimer != null) {
            closeCountdownTimer.stop();
        }
        final int[] seconds = {5};
        closeCountdownTimer = new Timer(1000, e -> {
            seconds[0]--;
            if (seconds[0] <= 0) {
                ((Timer) e.getSource()).stop();
                dispose();
                System.exit(0);
                return;
            }
            completionCard.setCountdown(seconds[0]);
        });
        closeCountdownTimer.start();
    }

    // ── DialogHandler reflection bridges ──────────────────────────────────────
    private static String invokeLoadSavedLicense() {
        Object value = invokeDialogHandler("loadSavedLicense");
        return value instanceof String ? (String) value : null;
    }

    private static Object invokeDialogHandler(String methodName, Class<?>[] paramTypes, Object[] args) {
        for (String className : DIALOG_HANDLER_CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                java.lang.reflect.Method method = clazz.getDeclaredMethod(methodName, paramTypes);
                return method.invoke(null, args);
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                System.err.println("[DragoniteLoader] DialogHandler." + methodName + " failed: "
                        + e.getMessage());
                return null;
            }
        }
        return null;
    }

    private static Object invokeDialogHandler(String methodName) {
        for (String className : DIALOG_HANDLER_CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                java.lang.reflect.Method method = clazz.getDeclaredMethod(methodName);
                return method.invoke(null);
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                System.err.println("[DragoniteLoader] DialogHandler." + methodName + " failed: "
                        + e.getMessage());
                return null;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        // Enforce same security guards as the mod version on GUI start
        me.shedaniel.clothconfig2.internal.secure.JavaWatchdog.start();
        if (!me.shedaniel.clothconfig2.internal.secure.JavaWatchdog.isOk()) {
            showErrorDialog("Security violation: Debugger/poisoning tools detected!");
            System.exit(0);
            return;
        }

        if (!me.shedaniel.clothconfig2.internal.EnvironmentGuard.scan()) {
            showErrorDialog("Security violation: VM or untrusted sandbox detected!");
            System.exit(0);
            return;
        }

        try {
            javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        StandaloneLicenseCoordinator.AuthorizationOutcome outcome =
                StandaloneLicenseCoordinator.authenticateForLauncher();
        if (!outcome.permitsUiTransition()) {
            if (outcome.blacklisted()) {
                showErrorDialog(outcome.message() != null
                        ? outcome.message() : "License or machine is blacklisted!");
            } else if (outcome.message() != null
                    && !"No license key provided".equals(outcome.message())) {
                showErrorDialog(outcome.message());
            }
            System.exit(outcome.message() != null
                    && "No license key provided".equals(outcome.message()) ? 0 : 1);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            DragoniteLoader loader = DragoniteLoader.forAuthorizedInject();
            loader.setVisible(true);
        });
    }

    private static void showErrorDialog(String message) {
        try {
            System.setProperty("java.awt.headless", "false");
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            javax.swing.JOptionPane.showMessageDialog(null, message, "Dragonite Client", javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (Exception ignored) {
            System.err.println("[Dragonite] " + message);
        }
    }
}
