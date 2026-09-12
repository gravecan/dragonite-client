package me.shedaniel.clothconfig2.internal;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class DialogHandler {

    private static final String PLACEHOLDER = "XXXX-XXXX-XXXX-XXXX-XXXX";
    private static Image headerImage = null;

    static { System.setProperty("java.awt.headless", "false"); }

    
    
    

    
    public static void saveLicense(String license) {
        SavedLicenseVault.save(license);
    }

    public static String loadSavedLicense() {
        return SavedLicenseVault.load();
    }

    public static void clearSavedLicense() {
        SavedLicenseVault.clear();
    }

    
    
    

    
    private static Image loadHeaderImage() {
        if (headerImage != null) return headerImage;
        System.out.println("[DialogHandler] Loading header image...");
        try {
            InputStream is = DialogHandler.class.getClassLoader()
                                             .getResourceAsStream("me/shedaniel/clothconfig2/impl/res/headertext.png");
            System.out.println("[DialogHandler] Resource stream: " + (is != null ? "OK" : "NULL"));
            if (is != null) {
                headerImage = javax.imageio.ImageIO.read(is);
                System.out.println("[DialogHandler] Image loaded: " + (headerImage != null ? "OK" : "FAILED"));
                is.close();
            }
        } catch (Exception e) {
            System.err.println("[DialogHandler] Failed to load header image: " + e.getMessage());
            e.printStackTrace();
        }
        return headerImage;
    }

    
    
    

    private static class Particle {
        double x, y, vx, vy;
        int radius;
        Particle(int w, int h, Random rng) {
            x = rng.nextDouble() * Math.max(w, 500);
            y = rng.nextDouble() * Math.max(h, 400);
            double angle = rng.nextDouble() * Math.PI * 2;
            double speed = 0.1 + rng.nextDouble() * 0.15; 
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed;
            radius = 1 + rng.nextInt(2); 
        }
        void tick(int w, int h) {
            x += vx; y += vy;
            if (x < 0) { x = 0; vx *= -1; } else if (x > w) { x = w; vx *= -1; }
            if (y < 0) { y = 0; vy *= -1; } else if (y > h) { y = h; vy *= -1; }
        }
    }

    private static class BackgroundPanel extends JPanel {
        private static final Color BG_1 = new Color(12, 14, 22);
        private static final Color BG_2 = new Color(5, 6, 10);
        private static final int R = 14;

        private final java.util.List<Particle> particles = new java.util.ArrayList<>();
        private final Timer timer;
        private int mouseX = -1000, mouseY = -1000;

        BackgroundPanel() {
            setOpaque(false);
            Random rng = new Random();
            for (int i = 0; i < 25; i++) { 
                particles.add(new Particle(600, 500, rng));
            }
            
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
                @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { mouseX = -1000; mouseY = -1000; }
            });

            timer = new Timer(16, e -> { 
                int w = getWidth(), h = getHeight();
                if (w > 0 && h > 0) {
                    for (Particle p : particles) p.tick(w, h);
                }
                repaint(); 
            });
            timer.start();
        }

        void stop() { if (timer != null) timer.stop(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            Shape clip = new RoundRectangle2D.Float(0, 0, w, h, R, R);
            g2.setClip(clip);

            
            GradientPaint gp = new GradientPaint(0, 0, BG_1, 0, h, BG_2);
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w, h, R, R);
            
            
            RadialGradientPaint rgp = new RadialGradientPaint(
                w / 2f, h / 2f, Math.max(w, h) / 1.2f,
                new float[]{0f, 1f},
                new Color[]{new Color(35, 50, 100, 45), new Color(0, 0, 0, 0)}
            );
            g2.setPaint(rgp);
            g2.fillRoundRect(0, 0, w, h, R, R);
            
            
            double maxDist = 110.0;
            g2.setStroke(new BasicStroke(0.8f));
            for (int i = 0; i < particles.size(); i++) {
                Particle p1 = particles.get(i);
                for (int j = i + 1; j < particles.size(); j++) {
                    Particle p2 = particles.get(j);
                    double dist = Math.hypot(p1.x - p2.x, p1.y - p2.y);
                    if (dist < maxDist) {
                        int alpha = (int) (40 * (1.0 - (dist / maxDist))); 
                        g2.setColor(new Color(100, 150, 255, alpha));
                        g2.drawLine((int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y);
                    }
                }
                
                
                double mDist = Math.hypot(p1.x - mouseX, p1.y - mouseY);
                if (mDist < 160) {
                    int alpha = (int) (70 * (1.0 - (mDist / 160.0))); 
                    g2.setColor(new Color(150, 200, 255, alpha));
                    g2.drawLine((int)p1.x, (int)p1.y, mouseX, mouseY);
                }
                
                
                g2.setColor(new Color(180, 210, 255, 60)); 
                g2.fillOval((int)p1.x - p1.radius, (int)p1.y - p1.radius, p1.radius * 2, p1.radius * 2);
            }
            
            
            g2.setClip(null);
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(new Color(255, 255, 255, 15));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, R, R);
            
            g2.dispose();
        }
    }

    
    
    

    private static class InputBorder extends AbstractBorder {
        private static final int R = 7;
        private boolean focused;
        private final AtomicInteger tick = new AtomicInteger(0);
        private Timer pulse;
        private final JComponent owner;

        InputBorder(JComponent owner) { this.owner = owner; }

        void setFocused(boolean f) {
            focused = f;
            if (f) {
                if (pulse == null) pulse = new Timer(38, e -> { tick.addAndGet(2); });
                pulse.start();
            } else {
                if (pulse != null) pulse.stop();
                tick.set(0); 
            }
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (focused) {
                float p = 0.5f + 0.5f * (float) Math.sin(tick.get() * 0.09);
                
                g2.setColor(new Color(75, 165, 255, (int)(30 + 30 * p)));
                g2.setStroke(new BasicStroke(3.5f));
                g2.drawRoundRect(x, y, w - 1, h - 1, R + 2, R + 2);
                
                g2.setColor(new Color(100, 180, 255, (int)(180 + 75 * p)));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, R, R);
            } else {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, R, R);
            }
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c)             { return new Insets(10, 15, 10, 15); }
        @Override public Insets getBorderInsets(Component c, Insets i)   { i.set(10, 15, 10, 15); return i; }
    }

    
    
    

    private static JButton makeButton(String label, Color base, Color hover) {
        return new JButton(label) {
            private float scale = 1f;
            private boolean isHovered = false;
            private Timer spring;

            {
                getModel().addChangeListener(e -> {
                    boolean pr = getModel().isPressed();
                    float tgt = pr ? 0.96f : 1f;
                    if (spring != null) spring.stop();
                    spring = new Timer(16, null);
                    spring.addActionListener(ev -> {
                        scale += (tgt - scale) * 0.35f;
                        if (Math.abs(tgt - scale) < 0.002f) { scale = tgt; spring.stop(); }
                    });
                    spring.start();
                });
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { isHovered = true; }
                    @Override public void mouseExited(MouseEvent e) { isHovered = false; }
                });
                setFont(uiFont(Font.PLAIN, 14f));
                setForeground(Color.WHITE);
                setBorder(BorderFactory.createEmptyBorder(11, 0, 11, 0));
                setFocusPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void repaint(long tm, int x, int y, int width, int height) {
                
                
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (scale != 1f) {
                    g2.translate(w * (1 - scale) / 2f, h * (1 - scale) / 2f);
                    g2.scale(scale, scale);
                }
                
                int r = h; 
                
                if (isHovered) {
                    
                    g2.setColor(new Color(hover.getRed(), hover.getGreen(), hover.getBlue(), 40));
                    g2.fillRoundRect(0, 0, w, h, r, r);
                    
                    g2.setColor(new Color(hover.getRed(), hover.getGreen(), hover.getBlue(), 80));
                    g2.fillRoundRect(2, 2, w-4, h-4, r-4, r-4);
                    
                    g2.setColor(new Color(hover.getRed(), hover.getGreen(), hover.getBlue(), 200));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(2, 2, w-4, h-4, r-4, r-4);
                } else {
                    
                    g2.setColor(new Color(255, 255, 255, 8)); 
                    g2.fillRoundRect(2, 2, w-4, h-4, r-4, r-4);
                    
                    g2.setColor(new Color(255, 255, 255, 20)); 
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.drawRoundRect(2, 2, w-4, h-4, r-4, r-4);
                }
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    
    
    

    private static JButton makeCloseButton(Runnable onClose) {
        JButton btn = new JButton("×") {
            @Override
            public void repaint(long tm, int x, int y, int width, int height) {
                
            }

            @Override protected void paintComponent(Graphics g) {
                if (getModel().isRollover()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(190, 50, 50, 110));
                    g2.fillRoundRect(3, 3, getWidth()-6, getHeight()-6, 5, 5);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        btn.setForeground(new Color(100, 108, 135));
        btn.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(26, 26));
        btn.addActionListener(e -> onClose.run());
        return btn;
    }

    
    
    

    private static class LicenseFilter extends DocumentFilter {
        private boolean busy;

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            if (length == 1) {
                String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                
                if (offset >= 0 && offset < cur.length() && cur.charAt(offset) == '-') {
                    if (offset > 0) { offset--; length = 2; }
                }
            }
            replace(fb, offset, length, "", null);
        }

        @Override
        public void replace(FilterBypass fb, int off, int len, String text, AttributeSet a)
                throws BadLocationException {
            if (busy) { super.replace(fb, off, len, text, a); return; }
            busy = true;
            try {
                String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                int safeLen = Math.min(len, cur.length() - off);
                String raw = (cur.substring(0, off) + (text == null ? "" : text) + cur.substring(off + safeLen))
                             .replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (raw.length() > 20) raw = raw.substring(0, 20);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < raw.length(); i++) { if (i > 0 && i % 4 == 0) sb.append('-'); sb.append(raw.charAt(i)); }
                fb.remove(0, fb.getDocument().getLength());
                super.insertString(fb, 0, sb.toString(), a);
            } catch (Exception ignored) {
                
            } finally { busy = false; }
        }
    }

    
    
    

    private static void addDrag(Window win, JComponent handle) {
        final Point[] start = {null};
        handle.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) { start[0] = e.getPoint(); }
            @Override public void mouseReleased(MouseEvent e) { start[0] = null; }
        });
        handle.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (start[0] == null) return;
                Point s = e.getLocationOnScreen();
                win.setLocation(s.x - start[0].x, s.y - start[0].y);
            }
        });
    }

    
    
    

    private static Font uiFont(int style, float size) {
        Font f = new Font("Segoe UI", style, (int) size);
        if (!f.getFamily().equalsIgnoreCase("Segoe UI"))
            f = new Font(Font.SANS_SERIF, style, (int) size);
        return f.deriveFont(size);
    }

    
    
    
    

    
    public static String showLicenseDialog() {
        return showLicenseDialog(true);
    }

    /**
     * @param allowSavedShortcut when false, always shows the interactive dialog
     *        (used after a rejected saved or typed key).
     */
    public static String showLicenseDialog(boolean allowSavedShortcut) {
        System.out.println("[DialogHandler] === showLicenseDialog START ===");

        // Failsafe checks: read license from property, env var, or local file to bypass HeadlessException crashes
        String propKey = System.getProperty("dragonite.license");
        if (propKey != null) {
            System.clearProperty("dragonite.license");
        }
        if (propKey != null && !propKey.isBlank()) {
            System.out.println("[DialogHandler] Found license in system property");
            return propKey.trim().toUpperCase();
        }

        String envKey = System.getenv("DRAGONITE_LICENSE");
        if (envKey != null && !envKey.isBlank()) {
            System.out.println("[DialogHandler] Found license in environment variable");
            return envKey.trim().toUpperCase();
        }

        // No local license file. VPS remembers via HWID after first successful auth.
        // allowSavedShortcut kept for API compat; always falls through to dialog / HWID login.

        System.setProperty("java.awt.headless", "false");
        System.out.println("[DialogHandler] Set java.awt.headless=false");
        CountDownLatch          latch = new CountDownLatch(1);
        AtomicReference<String> res   = new AtomicReference<>(null);
        System.out.println("[DialogHandler] Created latch and result holder");

        Thread gui = new Thread(() -> {
            System.out.println("[DialogHandler] GUI thread started");
            try {
                System.out.println("[DialogHandler] Initializing Toolkit...");
                Toolkit.getDefaultToolkit();
                System.out.println("[DialogHandler] Toolkit initialized OK");
                System.out.println("[DialogHandler] Calling SwingUtilities.invokeAndWait...");
                SwingUtilities.invokeAndWait(() -> {
                    System.out.println("[DialogHandler] Inside invokeAndWait - building GUI");
                    try {
                        buildAndShow(res, latch);
                        System.out.println("[DialogHandler] buildAndShow completed");
                    } catch (HeadlessException e) {
                        System.err.println("[DialogHandler] HeadlessException: " + e.getMessage());
                        e.printStackTrace();
                        fallback(res, latch);
                    } catch (Exception e) {
                        System.err.println("[DialogHandler] Exception in buildAndShow: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                System.out.println("[DialogHandler] invokeAndWait returned");
            } catch (Exception e) {
                System.err.println("[DialogHandler] GUI thread exception: " + e.getClass().getName() + " - " + e.getMessage());
                e.printStackTrace();
                fallback(res, latch);
            }
        }, "cloth-license-gui");
        gui.setDaemon(true);
        System.out.println("[DialogHandler] Starting GUI thread");
        gui.start();
        System.out.println("[DialogHandler] GUI thread started, waiting on latch");

        try {
            latch.await();
            System.out.println("[DialogHandler] Latch released");
        } catch (InterruptedException e) {
            System.err.println("[DialogHandler] Interrupted while waiting");
            Thread.currentThread().interrupt();
        }
        System.out.println("[DialogHandler] Returning result: " + (res.get() != null ? "license provided" : "null"));
        return res.get();
    }

    private static void fallback(AtomicReference<String> result, CountDownLatch latch) {
        System.out.println("\n=== " + BuildFingerprint.decrypt("1b2d3e383031362b3a7f13363c3a312c3a") + " ===");
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("[DialogHandler] Headless environment detected - skipping interactive console input to avoid hang");
            latch.countDown();
            return;
        }
        System.out.print("Enter your license key: ");
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
            String k = r.readLine();
            result.set((k != null && !k.isBlank()) ? k.trim().toUpperCase() : null);
        } catch (IOException ignored) {}
        latch.countDown();
    }

    
    
    

    private static void buildAndShow(AtomicReference<String> result, CountDownLatch latch) {
        
        UIManager.put("AuditoryCues.playList", UIManager.get("AuditoryCues.noAuditoryCues"));

        Image header = loadHeaderImage();

        
        JFrame frame = new JFrame(BuildFingerprint.decrypt("1b2d3e383031362b3a7f13363c3a312c3a"));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setResizable(false);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));


        
        BackgroundPanel bg = new BackgroundPanel();
        bg.setLayout(new BorderLayout());

        
        Runnable doExit = () -> { bg.stop(); frame.dispose(); latch.countDown(); };

        
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        topBar.setOpaque(false);
        topBar.add(makeCloseButton(doExit));
        bg.add(topBar, BorderLayout.NORTH);

        
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(0, 36, 28, 36));
        bg.add(body, BorderLayout.CENTER);

        
        JLabel titleLbl;
        if (header != null) {
            int iw = 280, ih = (int)((double) header.getHeight(null) / header.getWidth(null) * iw);
            BufferedImage hq = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            Graphics2D hqg = hq.createGraphics();
            hqg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            hqg.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
            hqg.drawImage(header, 0, 0, iw, ih, null);
            hqg.dispose();
            titleLbl = new JLabel(new ImageIcon(hq));
        } else {
            titleLbl = new JLabel(BuildFingerprint.decrypt("1b0d1e181011160b1a"));
            titleLbl.setFont(uiFont(Font.BOLD, 28f));
            titleLbl.setForeground(new Color(225, 230, 245));
        }
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        
        JLabel subLbl = new JLabel("Enter your license key to continue");
        subLbl.setFont(uiFont(Font.PLAIN, 12f));
        subLbl.setForeground(new Color(95, 104, 138));
        subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        
        JPanel divider = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                int w = getWidth();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,0), w/2f, 0, new Color(255,255,255,25)));
                g2.fillRect(0, 0, w/2, 1);
                g2.setPaint(new GradientPaint(w/2f, 0, new Color(255,255,255,25), w, 0, new Color(255,255,255,0)));
                g2.fillRect(w/2, 0, w/2, 1);
                g2.dispose();
            }
        };
        divider.setOpaque(false);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(10, 1));
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);

        
        JTextField field = new JTextField();
        field.setFont(new Font("Consolas", Font.PLAIN, 14));
        field.setBackground(new Color(7, 8, 14));
        field.setForeground(new Color(195, 207, 240));
        field.setCaretColor(new Color(75, 155, 255));
        field.setOpaque(true);
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setPreferredSize(new Dimension(320, 42));
        
        Action oldBackspace = field.getActionMap().get(DefaultEditorKit.deletePrevCharAction);
        if (oldBackspace != null) {
            field.getActionMap().put(DefaultEditorKit.deletePrevCharAction, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (field.getDocument().getLength() == 0 || (field.getSelectionStart() == field.getSelectionEnd() && field.getCaretPosition() == 0)) {
                        return; 
                    }
                    oldBackspace.actionPerformed(e);
                }
            });
        }

        InputBorder ib = new InputBorder(field);
        field.setBorder(ib);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new LicenseFilter());
        field.setText(PLACEHOLDER);
        field.setForeground(new Color(72, 80, 108));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                ib.setFocused(true);
                if (PLACEHOLDER.equals(field.getText())) { field.setText(""); field.setForeground(new Color(195, 207, 240)); }
            }
            @Override public void focusLost(FocusEvent e) {
                ib.setFocused(false);
                if (field.getText() == null || field.getText().isBlank()) { field.setText(PLACEHOLDER); field.setForeground(new Color(72, 80, 108)); }
            }
        });

        
        JLabel errLbl = new JLabel(" ");
        errLbl.setFont(uiFont(Font.PLAIN, 11f));
        errLbl.setForeground(new Color(215, 75, 75));
        errLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        
        JButton activateBtn = makeButton("Activate",
            new Color(45, 135, 85),
            new Color(40, 200, 255)); 
        JButton exitBtn = makeButton("Exit",
            new Color(75, 80, 100),
            new Color(220, 70, 90)); 

        
        activateBtn.setPreferredSize(new Dimension(150, 40));
        activateBtn.setMaximumSize(new Dimension(150, 40));
        exitBtn.setPreferredSize(new Dimension(150, 40));
        exitBtn.setMaximumSize(new Dimension(150, 40));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(activateBtn);
        btnRow.add(exitBtn);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        
        body.add(titleLbl);
        body.add(Box.createVerticalStrut(6));
        body.add(subLbl);
        body.add(Box.createVerticalStrut(14));
        body.add(divider);
        body.add(Box.createVerticalStrut(16));
        body.add(field);
        body.add(Box.createVerticalStrut(5));
        body.add(errLbl);
        body.add(Box.createVerticalStrut(12));
        body.add(btnRow);

        
        activateBtn.addActionListener(e -> {
            String raw = field.getText();
            String lic = LicenseKey.normalize(raw);
            if (lic == null && PLACEHOLDER.equals(raw == null ? "" : raw.trim().toUpperCase())) {
                errLbl.setText("⚠  Please enter a license key"); field.requestFocusInWindow(); return;
            }
            if (lic == null) { errLbl.setText("⚠  Use the format XXXX-XXXX-XXXX-XXXX-XXXX"); field.requestFocusInWindow(); return; }
            result.set(lic);
            bg.stop(); frame.dispose(); latch.countDown();
        });
        exitBtn.addActionListener(e -> doExit.run());
        field.addActionListener(e -> activateBtn.doClick());

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doExit.run(); }
            @Override public void windowClosed (WindowEvent e) { if (latch.getCount() > 0) latch.countDown(); }
        });

        
        addDrag(frame, bg);
        addDrag(frame, topBar);

        frame.setContentPane(bg);
        frame.pack();
        
        if (frame.getWidth() < 400) {
            frame.setSize(400, frame.getHeight());
        }
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
    }

    
    
    

    
    public static void showError(String message) {
        String displayMessage = normalizeDialogText(message, "An unexpected client error occurred. Please check latest.log.");
        if (displayMessage.equals("An unexpected client error occurred. Please check latest.log.")) {
            System.err.println("[DialogHandler] ERROR: null or blank message supplied");
            new IllegalStateException("DialogHandler.showError received no message").printStackTrace();
        } else {
            System.err.println("[DialogHandler] ERROR: " + displayMessage);
        }
        showModalMessage(displayMessage, "Client Error", JOptionPane.ERROR_MESSAGE);
    }

    
    public static void showSuccess(String message) {
        System.out.println("[DialogHandler] SUCCESS: " + message);
        showModalMessage(message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showModalMessage(String message, String title, int messageType) {
        String displayTitle = normalizeDialogText(title, "ClothConfig");
        Runnable show = () -> {
            JDialog dialog = new JDialog((Frame) null, displayTitle, true);
            dialog.setAlwaysOnTop(true);
            JOptionPane.showMessageDialog(dialog, message, displayTitle, messageType);
            dialog.dispose();
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                show.run();
            } else {
                SwingUtilities.invokeAndWait(show);
            }
        } catch (Exception e) {
            System.err.println("[DialogHandler] Failed to show dialog: " + e.getMessage());
        }
    }

    private static String normalizeDialogText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return fallback;
        }
        boolean hasRenderableGlyph = stripped.codePoints().anyMatch(cp ->
                !Character.isWhitespace(cp)
                        && !Character.isISOControl(cp)
                        && Character.getType(cp) != Character.FORMAT);
        return hasRenderableGlyph ? value : fallback;
    }
}
