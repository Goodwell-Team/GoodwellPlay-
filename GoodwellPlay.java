/*
 * Goodwell Play! — лаунчер игр с достижениями.
 *
 * Запуск (нужна Java 17+), из папки, где лежат GoodwellPlay.java и images/:
 *     java GoodwellPlay.java
 * или
 *     javac GoodwellPlay.java && java GoodwellPlay
 *
 * Картинки ищутся в таком порядке: аргумент командной строки, -Dgw.images=...,
 * папка images рядом с GoodwellPlay.java, ./images, ~/.goodwellplay/images.
 * Найденные картинки копируются в ~/.goodwellplay/images, поэтому дальше
 * лаунчер работает из любой папки. Без картинок он тоже запустится (на эмодзи).
 *
 * Данные лежат в ~/.goodwellplay (игры, прогресс достижений, обложки).
 */
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GoodwellPlay {
    public static void main(String[] args) {
        Img.init(args);
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new MainWindow().setVisible(true);
        });
    }
}

/* ============================================================
 *  Оформление
 * ============================================================ */
final class Theme {
    // Глубокий индиго + мандарин: не «чёрный с неоном», а тёплый вечер за играми
    static final Color BG = new Color(0x15132E);
    static final Color SIDEBAR = new Color(0x1C1940);
    static final Color CARD = new Color(0x262255);
    static final Color CARD_HI = new Color(0x342E70);
    static final Color ACCENT = new Color(0xFF8A3D);
    static final Color PINK = new Color(0xFF4F87);
    static final Color MINT = new Color(0x5EEAD4);
    static final Color GOLD = new Color(0xFFD166);
    static final Color TEXT = new Color(0xF1EEFF);
    static final Color MUTED = new Color(0x9E9AC8);
    static final Color DANGER = new Color(0x6B2A4E);
    static final Color OK = new Color(0x34D399);

    private static final Map<Integer, Font> FONTS = new HashMap<>();
    static final String UI_FONT = pick("Segoe UI", "SF Pro Text", "Helvetica Neue", "Ubuntu", "Noto Sans", "DejaVu Sans");
    static final String EMOJI_FONT = pick("Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", "Segoe UI Symbol", "DejaVu Sans");

    private static String pick(String... names) {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (have.contains(n)) return n;
        return Font.SANS_SERIF;
    }

    static Font font(int style, int size) {
        return FONTS.computeIfAbsent(style * 1000 + size, k -> new Font(UI_FONT, style, size));
    }

    static Font emoji(int size) {
        return FONTS.computeIfAbsent(-size, k -> new Font(EMOJI_FONT, Font.PLAIN, size));
    }

    static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        ToolTipManager.sharedInstance().setInitialDelay(300);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", SIDEBAR);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", font(Font.PLAIN, 14));
        UIManager.put("OptionPane.buttonFont", font(Font.BOLD, 13));
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", font(Font.PLAIN, 14));
        UIManager.put("TextField.background", CARD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("Button.background", CARD_HI);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("Button.select", CARD);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.background", BG);
        UIManager.put("ToolTip.background", CARD_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("PopupMenu.background", CARD);
        UIManager.put("MenuItem.background", CARD);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", ACCENT);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.font", font(Font.PLAIN, 13));
    }

    static Image appIcon() {
        BufferedImage m = Img.get("mascot", 64);
        if (m != null) {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.drawImage(m, (64 - m.getWidth()) / 2, (64 - m.getHeight()) / 2, null);
            g.dispose();
            return img;
        }
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Ui.aa(g);
        g.setPaint(new GradientPaint(0, 0, ACCENT, 64, 64, PINK));
        g.fillRoundRect(0, 0, 64, 64, 20, 20);
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, 40));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("G", (64 - fm.stringWidth("G")) / 2, (64 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }
}

/* ============================================================
 *  Картинки
 * ============================================================ */
final class Img {
    private static Path dir;
    private static final Map<String, BufferedImage> RAW = new HashMap<>();
    private static final Map<String, BufferedImage> SCALED = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /** Ищет папку с картинками. Если нашлась не в домашней папке — копирует туда. */
    static void init(String[] args) {
        List<Path> cand = new ArrayList<>();
        if (args != null) for (String a : args) cand.add(Paths.get(a));
        String prop = System.getProperty("gw.images");
        if (prop != null) cand.add(Paths.get(prop));
        // запуск «java GoodwellPlay.java»: путь к исходнику лежит в sun.java.command
        String cmd = System.getProperty("sun.java.command", "");
        int e = cmd.indexOf(".java");
        int sp = cmd.indexOf(' ');
        if (e > 0 && sp > 0 && sp < e) {
            Path src = Paths.get(cmd.substring(sp + 1, e + 5).trim()).toAbsolutePath().getParent();
            if (src != null) cand.add(src);
        }
        cand.add(Paths.get("").toAbsolutePath());
        try {
            Path code = Paths.get(GoodwellPlay.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            cand.add(Files.isRegularFile(code) ? code.getParent() : code);
        } catch (Exception ignored) {
        }

        Path home = Storage.DIR.resolve("images");
        for (Path p : cand) {
            if (p == null) continue;
            if (ok(p)) { dir = p; break; }
            if (ok(p.resolve("images"))) { dir = p.resolve("images"); break; }
        }
        if (dir == null && ok(home)) dir = home;
        if (dir != null && !dir.equals(home)) copyTo(home);
    }

    private static boolean ok(Path p) {
        return Files.isRegularFile(p.resolve("rocket.png"));
    }

    private static void copyTo(Path home) {
        try {
            Files.createDirectories(home);
            try (var s = Files.list(dir)) {
                for (Path f : (Iterable<Path>) s::iterator) {
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".png")) Files.copy(f, home.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ignored) {
        }
    }

    static BufferedImage raw(String name) {
        if (name == null || dir == null || MISSING.contains(name)) return null;
        BufferedImage img = RAW.get(name);
        if (img != null) return img;
        try {
            Path p = dir.resolve(name + ".png");
            if (Files.isRegularFile(p)) img = ImageIO.read(p.toFile());
        } catch (Exception ignored) {
        }
        if (img == null) MISSING.add(name);
        else RAW.put(name, img);
        return img;
    }

    /** Вписывает картинку в квадрат box×box с сохранением пропорций. */
    static BufferedImage get(String name, int box) {
        String key = name + "@" + box;
        BufferedImage c = SCALED.get(key);
        if (c != null) return c;
        BufferedImage src = raw(name);
        if (src == null) return null;
        double k = Math.min((double) box / src.getWidth(), (double) box / src.getHeight());
        c = scale(src, Math.max(1, (int) Math.round(src.getWidth() * k)), Math.max(1, (int) Math.round(src.getHeight() * k)));
        SCALED.put(key, c);
        return c;
    }

    static ImageIcon icon(String name, int box) {
        BufferedImage i = get(name, box);
        return i == null ? null : new ImageIcon(i);
    }

    /** Серая полупрозрачная версия — для закрытых достижений. */
    static BufferedImage gray(String name, int box) {
        String key = "g:" + name + "@" + box;
        BufferedImage c = SCALED.get(key);
        if (c != null) return c;
        BufferedImage src = get(name, box);
        if (src == null) return null;
        c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < c.getHeight(); y++) {
            for (int x = 0; x < c.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                int l = (r * 30 + g * 59 + b * 11) / 100;
                c.setRGB(x, y, ((a * 45 / 100) << 24) | (l << 16) | (l << 8) | l);
            }
        }
        SCALED.put(key, c);
        return c;
    }

    /** Заполняет прямоугольник w×h картинкой, обрезая лишнее по краям (как object-fit: cover). */
    static BufferedImage fill(BufferedImage src, int w, int h) {
        double k = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
        int tw = Math.max(w, (int) Math.ceil(src.getWidth() * k));
        int th = Math.max(h, (int) Math.ceil(src.getHeight() * k));
        BufferedImage big = scale(src, tw, th);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(big, (w - tw) / 2, (h - th) / 2, null);
        g.dispose();
        return out;
    }

    /** Качественное уменьшение: ступенями вдвое, чтобы не было «лесенки». */
    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage cur = src;
        int cw = src.getWidth(), ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = step(cur, cw, ch);
        }
        return step(cur, w, h);
    }

    private static BufferedImage step(BufferedImage s, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return o;
    }
}

final class Ui {
    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /** Иконка достижения: картинка, серая картинка (закрыто), «?» (секрет) или эмодзи, если картинок нет. */
    static JComponent achIcon(Achievement a, int size, boolean locked, boolean hidden) {
        JLabel l = new JLabel("", SwingConstants.CENTER);
        l.setForeground(Theme.TEXT);
        if (hidden) {
            l.setText("?");
            l.setFont(Theme.font(Font.BOLD, size * 3 / 4));
            l.setForeground(Theme.MUTED);
            return l;
        }
        BufferedImage im = locked ? Img.gray(a.img, size) : Img.get(a.img, size);
        if (im != null) {
            l.setIcon(new ImageIcon(im));
        } else {
            l.setText(locked ? "🔒" : a.icon);
            l.setFont(Theme.emoji(size * 3 / 4));
        }
        return l;
    }

    static JTextField field() {
        JTextField f = new JTextField(28);
        f.setFont(Theme.font(Font.PLAIN, 14));
        f.setBackground(Theme.CARD);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Theme.CARD_HI, 1, true), new EmptyBorder(8, 10, 8, 10)));
        return f;
    }

    static void styleCombo(JComboBox<String> c) {
        c.setFont(Theme.font(Font.PLAIN, 13));
        c.setBackground(Theme.CARD);
        c.setForeground(Theme.TEXT);
        c.setFocusable(false);
        c.setPreferredSize(new Dimension(200, 42));
        c.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                BasicArrowButton b = new BasicArrowButton(SwingConstants.SOUTH, Theme.CARD, Theme.CARD, Theme.MUTED, Theme.CARD);
                b.setBorder(new EmptyBorder(0, 0, 0, 6));
                return b;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean hasFocus) {
                g.setColor(Theme.CARD);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
        c.setBorder(new LineBorder(Theme.CARD_HI, 1, true));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, v, i, sel, focus);
                setBorder(new EmptyBorder(7, 12, 7, 12));
                setBackground(sel ? Theme.ACCENT : Theme.CARD);
                setForeground(sel ? Color.WHITE : Theme.TEXT);
                return this;
            }
        });
    }

    static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(null);
        sp.setBackground(Theme.BG);
        sp.getViewport().setBackground(Theme.BG);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getVerticalScrollBar().setUnitIncrement(28);
        return sp;
    }

    static String ellipsize(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }
}

final class SlimScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
        thumbColor = Theme.CARD_HI;
        trackColor = Theme.BG;
    }

    @Override
    protected JButton createDecreaseButton(int o) {
        return zero();
    }

    @Override
    protected JButton createIncreaseButton(int o) {
        return zero();
    }

    private static JButton zero() {
        JButton b = new JButton();
        Dimension d = new Dimension(0, 0);
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(trackColor);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.aa(g2);
        g2.setColor(isThumbRollover() ? Theme.MUTED : thumbColor);
        g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
        g2.dispose();
    }
}

/** Скруглённая панель. */
class RoundPanel extends JPanel {
    Color fill;
    final int arc;

    RoundPanel(Color fill, int arc) {
        super(null);
        this.fill = fill;
        this.arc = arc;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.aa(g2);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Панель с лёгким «зерном» из grain.png поверх фона. */
final class GrainPanel extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage t = Img.raw("grain");
        if (t == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(0.07f));
        for (int y = 0; y < getHeight(); y += t.getHeight())
            for (int x = 0; x < getWidth(); x += t.getWidth()) g2.drawImage(t, x, y, null);
        g2.dispose();
    }
}

/** Кнопка со скруглением, иконкой и подсветкой при наведении. */
final class Btn extends JButton {
    private Color base;
    private boolean hover;

    Btn(String text, Color base) {
        super(text);
        this.base = base;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(Theme.font(Font.BOLD, 13));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(10, 16, 10, 16));
        setIconTextGap(10);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    Btn(String text, Color base, String icon, int iconSize) {
        this(text, base);
        ImageIcon ic = Img.icon(icon, iconSize);
        if (ic != null) setIcon(ic);
    }

    void setBase(Color c) {
        base = c;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.aa(g2);
        Color c;
        if (!isEnabled()) c = Theme.CARD;
        else if (getModel().isPressed()) c = base.darker();
        else if (hover) c = base.equals(Theme.SIDEBAR) ? Theme.CARD : base.brighter();
        else c = base;
        g2.setColor(c);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Горизонтальная полоска прогресса. */
final class Bar extends JComponent {
    private double value;
    private final Color fill;

    Bar(Color fill) {
        this.fill = fill;
        setPreferredSize(new Dimension(100, 8));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    void setValue(double v) {
        value = Math.max(0, Math.min(1, v));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.aa(g2);
        g2.setColor(Theme.BG);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        int w = (int) Math.round(getWidth() * value);
        if (w > 0) {
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, Math.max(w, getHeight()), getHeight(), getHeight(), getHeight());
        }
        g2.dispose();
    }
}

/** Поле поиска с подсказкой и значком лупы. */
final class SearchField extends JTextField {
    private final String hint;

    SearchField(String hint) {
        this.hint = hint;
        setOpaque(false);
        setForeground(Theme.TEXT);
        setCaretColor(Theme.ACCENT);
        setFont(Theme.font(Font.PLAIN, 15));
        setBorder(new EmptyBorder(11, 46, 11, 16));
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.aa(g);
        g.setColor(Theme.CARD);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        if (isFocusOwner()) {
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
        }
        int cy = getHeight() / 2;
        BufferedImage mag = Img.get("magnifier", 22);
        if (mag != null) {
            g.drawImage(mag, 14, cy - mag.getHeight() / 2, null);
        } else {
            g.setColor(Theme.MUTED);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(17, cy - 8, 12, 12);
            g.drawLine(27, cy + 3, 32, cy + 8);
        }
        g.dispose();
        super.paintComponent(g0);
        if (getText().isEmpty()) {
            Graphics2D h = (Graphics2D) g0.create();
            Ui.aa(h);
            h.setFont(getFont());
            h.setColor(Theme.MUTED);
            FontMetrics fm = h.getFontMetrics();
            h.drawString(hint, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            h.dispose();
        }
    }
}

/** Логотип. По нему можно кликать — но это секрет. */
final class Logo extends JComponent {
    Logo() {
        setPreferredSize(new Dimension(200, 44));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.aa(g);
        g.setPaint(new GradientPaint(0, 2, Theme.ACCENT, 40, 42, Theme.PINK));
        g.fillRoundRect(0, 2, 40, 40, 13, 13);
        g.setColor(Color.WHITE);
        g.setFont(Theme.font(Font.BOLD, 25));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("G", (40 - fm.stringWidth("G")) / 2, 2 + (40 - fm.getHeight()) / 2 + fm.getAscent());
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 17));
        fm = g.getFontMetrics();
        g.drawString("Goodwell Play!", 50, (44 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}

/** Маскот с облачком-подсказкой. Подсказки меняются сами, на клик и при открытии достижений. */
final class Mascot extends JComponent {
    private String text = "";

    Mascot() {
        setPreferredSize(new Dimension(204, 110));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void say(String t) {
        text = t;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.aa(g);
        int w = getWidth(), h = getHeight();
        BufferedImage m = Img.get("mascot", 100);
        int bx = 6;
        if (m != null) {
            g.drawImage(m, 0, h - m.getHeight(), null);
            bx = m.getWidth() + 14;
        }
        int bw = w - bx, bh = h - 14;
        g.setColor(Theme.CARD);
        g.fillRoundRect(bx, 0, bw, bh, 16, 16);
        g.fillPolygon(new int[]{bx + 1, bx - 9, bx + 1}, new int[]{bh - 30, bh - 18, bh - 12}, 3);

        g.setFont(Theme.font(Font.PLAIN, 12));
        g.setColor(Theme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        int maxW = bw - 22, lh = 16, maxLines = Math.max(1, (bh - 16) / lh);
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(t) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(t);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, Ui.ellipsize(lines.get(maxLines - 1) + "…", fm, maxW));
        }
        int y = (bh - lines.size() * lh) / 2 + fm.getAscent() - 1;
        for (String l : lines) {
            g.drawString(Ui.ellipsize(l, fm, maxW), bx + 11, y);
            y += lh;
        }
        g.dispose();
    }
}

/** Снег или дождь поверх окна. Прозрачен для мыши. */
final class Weather extends JComponent {
    private static final class P {
        float x, y, vx, vy, s, ph;
        boolean alt;
    }

    private final List<P> ps = new ArrayList<>();
    private final Random rnd = new Random();
    private final Timer timer = new Timer(50, e -> {
        step();
        repaint();
    });
    private int mode;   // 0 — выкл, 1 — снег, 2 — дождь

    Weather() {
        setOpaque(false);
    }

    @Override
    public boolean contains(int x, int y) {
        return false;   // клики проходят насквозь
    }

    int mode() {
        return mode;
    }

    void setMode(int m) {
        mode = m;
        ps.clear();
        setVisible(m != 0);
        if (m != 0) timer.start();
        else timer.stop();
        repaint();
    }

    private P spawn(boolean anywhere) {
        P p = new P();
        int w = Math.max(getWidth(), 1), h = Math.max(getHeight(), 1);
        p.x = rnd.nextFloat() * w;
        p.y = anywhere ? rnd.nextFloat() * h : -50;
        p.ph = rnd.nextFloat() * 6.28f;
        p.alt = rnd.nextBoolean();
        if (mode == 1) {
            p.s = 6 + rnd.nextFloat() * 9;
            p.vy = 1.2f + p.s * 0.12f;
        } else {
            p.s = 22 + rnd.nextFloat() * 22;
            p.vy = 12 + rnd.nextFloat() * 8;
        }
        return p;
    }

    private void step() {
        if (getWidth() == 0) return;
        int target = mode == 1 ? 80 : 110;
        while (ps.size() < target) ps.add(spawn(true));
        for (int i = 0; i < ps.size(); i++) {
            P p = ps.get(i);
            p.y += p.vy;
            if (mode == 1) {
                p.ph += 0.07f;
                p.x += (float) (Math.sin(p.ph) * 0.8);
            }
            if (p.y > getHeight() + 10) ps.set(i, spawn(false));
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (mode == 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        Ui.aa(g);
        for (P p : new ArrayList<>(ps)) {
            if (mode == 1) {
                BufferedImage im = Img.get(p.alt ? "snowflake" : "snow", (int) p.s);
                g.setComposite(AlphaComposite.SrcOver.derive(Math.min(0.9f, 0.35f + p.s / 24f)));
                if (im != null) {
                    g.drawImage(im, (int) p.x, (int) p.y, null);
                } else {
                    g.setColor(Color.WHITE);
                    g.fillOval((int) p.x, (int) p.y, (int) (p.s / 2), (int) (p.s / 2));
                }
            } else {
                BufferedImage im = Img.get("rain", (int) p.s);
                g.setComposite(AlphaComposite.SrcOver.derive(0.5f));
                if (im != null) {
                    g.drawImage(im, (int) p.x, (int) p.y, null);
                } else {
                    g.setColor(Theme.MINT);
                    g.drawLine((int) p.x, (int) p.y, (int) p.x, (int) (p.y + p.s));
                }
            }
        }
        g.dispose();
    }
}

/* ============================================================
 *  Модель и хранилище
 * ============================================================ */
final class Util {
    static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    static boolean isUri(String s) {
        return s.matches("(?i)^[a-z][a-z0-9+.\\-]*://.*");
    }

    static boolean isImage(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    static String duration(long sec) {
        if (sec < 60) return sec + " с";
        if (sec < 3600) return (sec / 60) + " мин";
        return (sec / 3600) + " ч " + ((sec % 3600) / 60) + " мин";
    }

    static String clock(long sec) {
        return String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    static String plural(long n, String one, String few, String many) {
        long a = Math.abs(n) % 100, b = a % 10;
        if (a > 10 && a < 20) return many;
        if (b > 1 && b < 5) return few;
        if (b == 1) return one;
        return many;
    }

    static String prettify(String s, boolean stripExt) {
        String t = s;
        int dot = t.lastIndexOf('.');
        if (stripExt && dot > 0 && t.length() - dot <= 9) t = t.substring(0, dot);
        return t.replace('_', ' ').trim();
    }

    static String guessName(File f) {
        String base = prettify(f.getName(), true);
        String low = base.toLowerCase(Locale.ROOT);
        Set<String> generic = Set.of("game", "start", "run", "launcher", "play", "main", "app", "launch");
        if (generic.contains(low) && f.getParentFile() != null) return prettify(f.getParentFile().getName(), false);
        return base;
    }

    static void browse(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Не удалось открыть ссылку:\n" + url);
        }
    }
}

final class Game {
    String id = UUID.randomUUID().toString();
    String name;
    String target;          // путь к файлу или ссылка (steam://rungameid/...)
    long playSeconds;
    int launches;
    long lastPlayed;
    boolean favorite;

    Game(String name, String target) {
        this.name = name;
        this.target = target;
    }

    boolean isUri() {
        return Util.isUri(target);
    }

    private float hue() {
        return ((name.hashCode() & 0x7fffffff) % 360) / 360f;
    }

    Color colorA() {
        return Color.getHSBColor(hue(), 0.62f, 0.88f);
    }

    Color colorB() {
        return Color.getHSBColor((hue() + 0.12f) % 1f, 0.75f, 0.55f);
    }

    String initials() {
        String[] parts = name.trim().split("[^\\p{L}\\p{N}]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(p.charAt(0)));
        if (sb.length() == 1 && parts[0].length() > 1) sb.append(Character.toLowerCase(parts[0].charAt(1)));
        return sb.length() == 0 ? "?" : sb.toString();
    }
}

final class Storage {
    static final Path DIR = Paths.get(System.getProperty("user.home"), ".goodwellplay");
    static final Path GAMES = DIR.resolve("games.tsv");
    static final Path STATS = DIR.resolve("stats.properties");

    private static long num(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static List<Game> loadGames() {
        List<Game> list = new ArrayList<>();
        if (!Files.exists(GAMES)) return list;
        try {
            for (String line : Files.readAllLines(GAMES, StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length < 7) continue;
                Game g = new Game(f[1], f[2]);
                g.id = f[0];
                g.playSeconds = num(f[3]);
                g.launches = (int) num(f[4]);
                g.lastPlayed = num(f[5]);
                g.favorite = "1".equals(f[6]);
                list.add(g);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    static void saveGames(List<Game> games) {
        try {
            Files.createDirectories(DIR);
            List<String> lines = new ArrayList<>();
            for (Game g : games) {
                lines.add(String.join("\t", g.id, clean(g.name), clean(g.target), String.valueOf(g.playSeconds),
                        String.valueOf(g.launches), String.valueOf(g.lastPlayed), g.favorite ? "1" : "0"));
            }
            Files.write(GAMES, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static Properties loadStats() {
        Properties p = new Properties();
        if (Files.exists(STATS)) {
            try (Reader r = Files.newBufferedReader(STATS, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return p;
    }

    static void saveStats(Properties p) {
        try {
            Files.createDirectories(DIR);
            try (Writer w = Files.newBufferedWriter(STATS, StandardCharsets.UTF_8)) {
                p.store(w, "Goodwell Play! stats");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/** Обложки игр: своя картинка, либо cover/poster/icon… рядом с игрой, либо градиент с инициалами. */
final class Covers {
    private static final Path DIR = Storage.DIR.resolve("covers");
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Map<String, Path> AUTO = new HashMap<>();
    private static final String[] NAMES = {"cover", "poster", "boxart", "banner", "header", "folder", "icon", "logo"};
    private static final String[] EXT = {".png", ".jpg", ".jpeg"};

    private static Path custom(Game g) {
        return DIR.resolve(g.id + ".png");
    }

    static boolean hasCustom(Game g) {
        return Files.exists(custom(g));
    }

    private static Path auto(Game g) {
        if (AUTO.containsKey(g.id)) return AUTO.get(g.id);
        Path found = null;
        if (!g.isUri()) {
            File parent = new File(g.target).getParentFile();
            if (parent != null) {
                outer:
                for (String n : NAMES) {
                    for (String e : EXT) {
                        File f = new File(parent, n + e);
                        if (f.isFile()) {
                            found = f.toPath();
                            break outer;
                        }
                    }
                }
            }
        }
        AUTO.put(g.id, found);
        return found;
    }

    /** Обложка нужного размера или null, если картинки нет. */
    static BufferedImage get(Game g, int w, int h) {
        String key = g.id + "@" + w + "x" + h;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        BufferedImage out = null;
        try {
            BufferedImage src = null;
            if (hasCustom(g)) src = ImageIO.read(custom(g).toFile());
            else if (auto(g) != null) src = ImageIO.read(auto(g).toFile());
            if (src != null) out = Img.fill(src, w, h);
        } catch (Exception ignored) {
        }
        CACHE.put(key, out);
        return out;
    }

    static boolean set(Game g, File image) {
        try {
            BufferedImage src = ImageIO.read(image);
            if (src == null) return false;
            Files.createDirectories(DIR);
            ImageIO.write(Img.fill(src, GameCard.W * 2, GameCard.COVER * 2), "png", custom(g).toFile());
            clear(g.id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void reset(Game g) {
        try {
            Files.deleteIfExists(custom(g));
        } catch (IOException ignored) {
        }
        clear(g.id);
    }

    private static void clear(String id) {
        CACHE.keySet().removeIf(k -> k.startsWith(id + "@"));
        AUTO.remove(id);
    }
}

/* ============================================================
 *  Достижения
 * ============================================================ */
final class Achievement {
    final String id, img, icon, title, desc;
    final int points;
    final boolean secret;
    long unlockedAt;

    Achievement(String id, String img, String icon, String title, String desc, int points, boolean secret) {
        this.id = id;
        this.img = img;
        this.icon = icon;
        this.title = title;
        this.desc = desc;
        this.points = points;
        this.secret = secret;
    }

    boolean unlocked() {
        return unlockedAt > 0;
    }
}

final class Achievements {
    final List<Achievement> all = new ArrayList<>();
    private final Map<String, Achievement> byId = new HashMap<>();
    private final Properties p;
    Consumer<Achievement> onUnlock = a -> { };

    Achievements(Properties p) {
        this.p = p;

        // Коллекция
        add("first_game", "box", "🎮", "Первый пошёл", "Добавь первую игру в библиотеку", 10, false);
        add("collector5", "harddrive", "📚", "Коллекционер", "5 игр в библиотеке", 15, false);
        add("hoarder15", "platform", "🐉", "Дракон на сокровищах", "15 игр. Пройдёшь максимум три, но это не важно", 30, false);
        add("fav1", "shortcut", "⭐", "Любимчик", "Отметь игру избранной", 10, false);
        add("fav5", "socials", "💖", "Фаворитизм", "5 избранных игр. У всех есть любимчики", 20, false);
        add("scan", "magnifier", "🔦", "Археолог", "Найди игры сканированием папки", 15, false);
        add("dragdrop", "cursor", "🧲", "Перетащил и забыл", "Добавь игру перетаскиванием файла в окно", 10, false);
        add("bye", "bin", "👋", "Прощай, старый друг", "Удали игру из библиотеки", 10, false);
        add("trash", "trash", "🗑️", "Генеральная уборка", "Удали 5 игр. Освободи место для новых", 20, false);
        add("urlgame", "cloud", "☁️", "Облачный геймер", "Добавь игру-ссылку, например steam://rungameid/730", 15, false);
        add("rename", "font", "🔤", "Переименовщик", "Переименуй игру. «Ведьмак 3» звучит лучше, чем witcher3.exe", 10, false);
        add("cover", "colors", "🎨", "Дизайнер", "Поставь игре свою обложку", 15, false);

        // Запуски и время
        add("first_launch", "rocket", "🚀", "Поехали!", "Запусти игру из лаунчера", 10, false);
        add("launch10", "reply", "🔁", "Режим привычки", "10 запусков игр", 20, false);
        add("launch50", "dice", "🎰", "Ещё одну катку", "50 запусков. Просто ещё одну", 35, false);
        add("multi", "gpu", "🔥", "Многозадачность", "Запусти две игры одновременно. Видеокарта в шоке", 25, false);
        add("night", "monitor", "🦉", "Сова-бессонница", "Запусти игру между 00:00 и 05:00. Завтра ведь на работу?", 25, false);
        add("morning", "brightness", "🐓", "Ранняя пташка", "Запусти игру между 05:00 и 08:00. Ты вообще спал?", 25, false);
        add("short", "crash", "💨", "Не зашло", "Закрой игру быстрее, чем за 10 секунд", 15, true);
        add("hour1", "updates", "⏰", "Ещё пять минуточек", "Час в играх суммарно", 15, false);
        add("marathon", "fps", "🛋️", "Диван — мой дом", "Одна сессия дольше 2 часов", 30, false);
        add("hour10", "engine", "🧟", "Солнце? Не слышал", "10 часов в играх суммарно", 40, false);
        add("quit", "logout", "🏃", "Побег", "Закрой лаунчер, пока идёт игра", 10, true);

        // Поиск и интерфейс
        add("search10", "debug", "🔎", "Следопыт", "Сделай 10 поисковых запросов", 15, false);
        add("nothing", "offline", "🕳️", "Найдено: пустота", "Найди то, чего в библиотеке нет", 10, false);
        add("store", "world-map", "🛒", "Шопоголик", "Открой поиск в магазине. Кошелёк, держись", 10, false);
        add("sorter", "flip", "🔀", "Сортировщик", "Попробуй все 4 способа сортировки", 15, false);
        add("ctx", "mouse", "🖱️", "Правая кнопка", "Открой меню игры правым кликом", 10, false);
        add("folder", "flashdrive", "📂", "Любопытный", "Открой папку с игрой из меню", 10, false);
        add("hotkey", "insert", "⌨️", "Горячие клавиши", "Нажми Ctrl+F или Ctrl+N. Мышь отдыхает", 10, false);
        add("fullscreen", "fullscreen", "🖥️", "На весь экран", "Нажми F11", 10, false);
        add("weather", "weather", "⛄", "Синоптик", "Включи погоду в лаунчере", 10, false);
        add("stats", "framerate", "📊", "Аналитик", "Открой статистику", 10, false);
        add("regular", "auth", "🙋", "Завсегдатай", "Запусти сам лаунчер 10 раз", 20, false);

        // Секреты и прикольчики
        add("logo10", "click", "👆", "Кликер", "Кликни по логотипу 10 раз", 10, true);
        add("logo50", "complain", "🤕", "Палец болит?", "50 кликов по логотипу", 25, true);
        add("konami", "mods", "🕹️", "Олдскул", "↑ ↑ ↓ ↓ ← → ← → B A", 40, true);
        add("bigbro", "eac", "👁️", "Большой брат", "Открой статистику 3 раза. Следишь за собой?", 15, true);
        add("achTab", "issue", "🏆", "Ачивкохолик", "Открой вкладку достижений 5 раз. Вдруг что-то новое?", 10, false);
        add("platinum", "celebrate", "💎", "Платиновый трофей", "Открой все остальные достижения", 100, false);

        for (Achievement a : all) a.unlockedAt = getLong("ach." + a.id);
    }

    private void add(String id, String img, String icon, String title, String desc, int pts, boolean secret) {
        Achievement a = new Achievement(id, img, icon, title, desc, pts, secret);
        all.add(a);
        byId.put(id, a);
    }

    long getLong(String k) {
        try {
            return Long.parseLong(p.getProperty(k, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    void set(String k, long v) {
        p.setProperty(k, String.valueOf(v));
    }

    long inc(String k) {
        long v = getLong(k) + 1;
        set(k, v);
        return v;
    }

    int unlockedCount() {
        return (int) all.stream().filter(Achievement::unlocked).count();
    }

    int points() {
        return all.stream().filter(Achievement::unlocked).mapToInt(a -> a.points).sum();
    }

    int maxPoints() {
        return all.stream().mapToInt(a -> a.points).sum();
    }

    boolean unlock(String id) {
        Achievement a = byId.get(id);
        if (a == null || a.unlocked()) return false;
        a.unlockedAt = System.currentTimeMillis();
        p.setProperty("ach." + id, String.valueOf(a.unlockedAt));
        onUnlock.accept(a);
        if (!id.equals("platinum")) {
            boolean everything = all.stream().allMatch(x -> x.id.equals("platinum") || x.unlocked());
            if (everything) unlock("platinum");
        }
        return true;
    }

    /** Проверяет достижения, зависящие от «накопленного» состояния. */
    void evaluate(int games, int favorites, long totalSeconds) {
        if (games >= 1) unlock("first_game");
        if (games >= 5) unlock("collector5");
        if (games >= 15) unlock("hoarder15");
        if (favorites >= 1) unlock("fav1");
        if (favorites >= 5) unlock("fav5");
        long launches = getLong("launches");
        if (launches >= 1) unlock("first_launch");
        if (launches >= 10) unlock("launch10");
        if (launches >= 50) unlock("launch50");
        if (totalSeconds >= 3600) unlock("hour1");
        if (totalSeconds >= 36000) unlock("hour10");
        if (getLong("searches") >= 10) unlock("search10");
        if (getLong("logo") >= 10) unlock("logo10");
        if (getLong("logo") >= 50) unlock("logo50");
        if (getLong("achTab") >= 5) unlock("achTab");
        if (getLong("removed") >= 5) unlock("trash");
        if (getLong("statsTab") >= 1) unlock("stats");
        if (getLong("statsTab") >= 3) unlock("bigbro");
        if (getLong("starts") >= 10) unlock("regular");
    }
}

/** Всплывашка «Достижение открыто». Несколько подряд встают в очередь. */
final class Toast extends JWindow {
    private static final Deque<Achievement> QUEUE = new ArrayDeque<>();
    private static boolean showing;

    static void post(JFrame owner, Achievement a) {
        QUEUE.add(a);
        if (!showing) next(owner);
    }

    private static void next(JFrame owner) {
        Achievement a = QUEUE.poll();
        if (a == null) {
            showing = false;
            return;
        }
        showing = true;
        new Toast(owner, a, () -> next(owner)).start();
    }

    private final Runnable done;

    private Toast(JFrame owner, Achievement a, Runnable done) {
        super(owner);
        this.done = done;
        setAlwaysOnTop(true);
        setFocusableWindowState(false);

        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setBackground(Theme.CARD_HI);
        root.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Theme.GOLD, 2), new EmptyBorder(14, 16, 14, 20)));

        JComponent icon = Ui.achIcon(a, 48, false, false);
        icon.setPreferredSize(new Dimension(56, 56));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel head = Ui.label("Достижение открыто  +" + a.points + " GW", 12, Font.BOLD, Theme.GOLD);
        JLabel title = Ui.label(a.title, 17, Font.BOLD, Theme.TEXT);
        JLabel desc = Ui.label("<html><body style='width:230px'>" + a.desc + "</body></html>", 12, Font.PLAIN, Theme.MUTED);
        title.setBorder(new EmptyBorder(3, 0, 3, 0));
        text.add(head);
        text.add(title);
        text.add(desc);

        root.add(icon, BorderLayout.WEST);
        root.add(text, BorderLayout.CENTER);
        setContentPane(root);
        pack();

        Rectangle b = owner.getBounds();
        setLocation(b.x + b.width - getWidth() - 24, b.y + b.height - getHeight() - 52);
    }

    private void opacity(float o) {
        try {
            setOpacity(Math.max(0f, Math.min(1f, o)));
        } catch (Exception ignored) {
            // на некоторых системах прозрачность окон не поддерживается — не страшно
        }
    }

    private void start() {
        opacity(0f);
        setVisible(true);
        long t0 = System.currentTimeMillis();
        Timer t = new Timer(25, null);
        t.addActionListener(e -> {
            long dt = System.currentTimeMillis() - t0;
            if (dt < 300) opacity(dt / 300f);
            else if (dt < 3800) opacity(1f);
            else if (dt < 4200) opacity(1f - (dt - 3800) / 400f);
            else {
                t.stop();
                dispose();
                done.run();
            }
        });
        t.start();
    }
}

/* ============================================================
 *  Поиск игр в папке
 * ============================================================ */
final class GameScanner {
    record Found(String name, String path) { }

    private static final String[] BAD = {"unins", "setup", "redist", "crash", "updater", "update", "helper", "install",
            "vcredist", "dxsetup", "dotnet", "webview", "cef", "notif", "report", "config", "7z", "python"};
    private static final Set<String> SKIP_DIRS = Set.of("redist", "_commonredist", "directx", "vcredist", "__installer",
            "dotnet", "redistributables", "prerequisites", "crashpad", "$recycle.bin", "node_modules", ".git");

    private static boolean isCandidate(String n, long size, boolean win) {
        for (String bad : BAD) if (n.contains(bad)) return false;
        if (n.endsWith(".jar")) return size > 50_000;
        if (win) return n.endsWith(".exe") && size > 300_000;
        return n.endsWith(".appimage") || n.endsWith(".sh") || n.endsWith(".x86_64");
    }

    private static String name(Path p) {
        return p.getFileName() == null ? "" : p.getFileName().toString();
    }

    /** Берёт самый большой исполняемый файл в каждой папке первого уровня: обычно это и есть игра. */
    static List<Found> scan(Path root, Set<String> known) {
        boolean win = Util.OS.contains("win");
        boolean mac = Util.OS.contains("mac");
        Map<String, Path> best = new LinkedHashMap<>();
        Map<String, Long> bestSize = new HashMap<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 4, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = name(dir).toLowerCase(Locale.ROOT);
                    if (!dir.equals(root) && SKIP_DIRS.contains(n)) return FileVisitResult.SKIP_SUBTREE;
                    if (mac && n.endsWith(".app")) {
                        offer(dir, Long.MAX_VALUE / 2);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    if (isCandidate(name(f).toLowerCase(Locale.ROOT), attrs.size(), win)) offer(f, attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

                private void offer(Path p, long size) {
                    Path rel = root.relativize(p);
                    String key = rel.getNameCount() > 1 ? rel.getName(0).toString() : rel.toString();
                    Long old = bestSize.get(key);
                    if (old == null || size > old) {
                        best.put(key, p);
                        bestSize.put(key, size);
                    }
                }
            });
        } catch (IOException ignored) {
        }

        List<Found> out = new ArrayList<>();
        for (Map.Entry<String, Path> e : best.entrySet()) {
            String path = e.getValue().toString();
            if (known.contains(path.toLowerCase(Locale.ROOT))) continue;
            boolean single = root.relativize(e.getValue()).getNameCount() == 1;
            out.add(new Found(Util.prettify(e.getKey(), single), path));
        }
        out.sort(Comparator.comparing((Found f) -> f.name().toLowerCase(Locale.ROOT)));
        return out;
    }
}

/* ============================================================
 *  Карточка игры в сетке
 * ============================================================ */
final class GameCard extends JComponent implements ListCellRenderer<Game> {
    static final int W = 176, H = 228, COVER = 132;
    private final Predicate<Game> isRunning;
    private final IntSupplier hoverIndex;
    private Game game;
    private boolean selected, running, hovered;

    GameCard(Predicate<Game> isRunning, IntSupplier hoverIndex) {
        this.isRunning = isRunning;
        this.hoverIndex = hoverIndex;
        setPreferredSize(new Dimension(W + 16, H + 16));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Game> l, Game g, int i, boolean sel, boolean focus) {
        game = g;
        selected = sel;
        running = isRunning.test(g);
        hovered = i == hoverIndex.getAsInt();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (game == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        Ui.aa(g);
        int x = 8, y = 8;

        g.setColor(selected || hovered ? Theme.CARD_HI : Theme.CARD);
        g.fillRoundRect(x, y, W, H, 22, 22);

        // обложка: своя картинка, либо градиент из названия + инициалы
        g.setClip(new RoundRectangle2D.Float(x, y, W, H, 22, 22));
        BufferedImage cover = Covers.get(game, W, COVER);
        if (cover != null) {
            g.drawImage(cover, x, y, null);
            g.setPaint(new GradientPaint(0, y + COVER - 46, new Color(0, 0, 0, 0), 0, y + COVER, new Color(0, 0, 0, 150)));
            g.fillRect(x, y + COVER - 46, W, 46);
        } else {
            g.setPaint(new GradientPaint(x, y, game.colorA(), x + W, y + COVER, game.colorB()));
            g.fillRect(x, y, W, COVER);
            g.setColor(new Color(255, 255, 255, 30));
            g.fillOval(x + W - 74, y - 34, 124, 124);
            g.fillOval(x - 36, y + 66, 96, 96);
            g.setColor(new Color(255, 255, 255, 240));
            g.setFont(Theme.font(Font.BOLD, 48));
            FontMetrics fm0 = g.getFontMetrics();
            String ini = game.initials();
            g.drawString(ini, x + (W - fm0.stringWidth(ini)) / 2, y + (COVER - fm0.getHeight()) / 2 + fm0.getAscent());
        }
        g.setClip(null);

        if (game.favorite) {
            g.setColor(Theme.GOLD);
            g.setFont(Theme.font(Font.BOLD, 22));
            g.drawString("★", x + W - 32, y + 28);
        }
        if (running) {
            g.setColor(Theme.OK);
            g.fillRoundRect(x + 10, y + COVER - 30, 74, 22, 22, 22);
            g.setColor(new Color(0x06281C));
            g.setFont(Theme.font(Font.BOLD, 12));
            g.drawString("В игре", x + 25, y + COVER - 14);
        }
        if (hovered) {
            // кнопка «играть» при наведении
            int d = 40, cx = x + W - d - 10, cy = y + COVER - d - 10;
            g.setColor(Theme.ACCENT);
            g.fillOval(cx, cy, d, d);
            g.setColor(Color.WHITE);
            g.fillPolygon(new int[]{cx + 15, cx + 15, cx + 29}, new int[]{cy + 11, cy + 29, cy + 20}, 3);
        }
        if (selected) {
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(x + 1, y + 1, W - 2, H - 2, 22, 22);
        }

        // подписи
        int tx = x + 14, tw = W - 28;
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.ellipsize(game.name, fm, tw), tx, y + COVER + 28);

        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 12));
        fm = g.getFontMetrics();
        String time = game.playSeconds == 0 ? "Ещё не наиграно" : Util.duration(game.playSeconds) + " в игре";
        g.drawString(time, tx, y + COVER + 50);
        String launches = game.launches + " " + Util.plural(game.launches, "запуск", "запуска", "запусков");
        g.drawString(launches, tx, y + COVER + 70);
        g.dispose();
    }
}

/* ============================================================
 *  Статистика
 * ============================================================ */
final class StatsView extends JComponent {
    private static final Color[] PALETTE = {Theme.ACCENT, Theme.PINK, Theme.GOLD, Theme.MINT, Theme.MUTED};
    private final Supplier<List<Game>> games;
    private final Properties stats;
    long live;   // секунды текущих сессий, которые ещё не записаны в игры

    StatsView(Supplier<List<Game>> games, Properties stats) {
        this.games = games;
        this.stats = stats;
        setOpaque(false);
    }

    private long day(LocalDate d) {
        try {
            return Long.parseLong(stats.getProperty("day." + d, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String shortTime(long s) {
        if (s <= 0) return "";
        if (s < 3600) return Math.max(1, s / 60) + " м";
        return String.format(Locale.ROOT, "%.1f ч", s / 3600.0).replace('.', ',');
    }

    private void tile(Graphics2D g, int x, int y, int w, int h, String icon, String value, String caption) {
        g.setColor(Theme.CARD);
        g.fillRoundRect(x, y, w, h, 18, 18);
        int tx = x + 16;
        BufferedImage im = Img.get(icon, 38);
        if (im != null) {
            g.drawImage(im, x + 14, y + (h - im.getHeight()) / 2, null);
            tx = x + 14 + 38 + 12;
        }
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 19));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.ellipsize(value, fm, x + w - tx - 8), tx, y + h / 2 - 2);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 12));
        fm = g.getFontMetrics();
        g.drawString(Ui.ellipsize(caption, fm, x + w - tx - 8), tx, y + h / 2 + 18);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.aa(g);
        int w = getWidth(), h = getHeight();
        List<Game> list = games.get();
        long total = list.stream().mapToLong(x -> x.playSeconds).sum() + live;
        long launches = list.stream().mapToLong(x -> x.launches).sum();
        LocalDate today = LocalDate.now();

        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 28));
        g.drawString("Статистика", 0, 30);

        int ty = 52, th = 84, gap = 14, tw = (w - gap * 3) / 4;
        tile(g, 0, ty, tw, th, "fps", Util.duration(total), "Всего в играх");
        tile(g, (tw + gap), ty, tw, th, "box", String.valueOf(list.size()), "Игр в библиотеке");
        tile(g, (tw + gap) * 2, ty, tw, th, "rocket", String.valueOf(launches), "Запусков");
        tile(g, (tw + gap) * 3, ty, tw, th, "brightness", Util.duration(day(today) + live), "Сегодня");

        int cy = ty + th + gap, ch = Math.max(190, h - cy);
        int half = (w - gap) / 2;

        // последние 7 дней
        g.setColor(Theme.CARD);
        g.fillRoundRect(0, cy, half, ch, 18, 18);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 15));
        g.drawString("Последние 7 дней", 18, cy + 30);
        long[] vals = new long[7];
        long max = 1;
        for (int i = 0; i < 7; i++) {
            vals[i] = day(today.minusDays(6 - i)) + (i == 6 ? live : 0);
            max = Math.max(max, vals[i]);
        }
        int top = cy + 56, bottom = cy + ch - 34, slot = (half - 36) / 7, bw = Math.max(10, slot - 14);
        for (int i = 0; i < 7; i++) {
            int bx = 18 + i * slot + (slot - bw) / 2;
            int bh = vals[i] == 0 ? 4 : Math.max(6, (int) ((bottom - top - 20) * vals[i] / (double) max));
            g.setColor(i == 6 ? Theme.ACCENT : vals[i] == 0 ? Theme.CARD_HI : Theme.MINT);
            g.fillRoundRect(bx, bottom - bh, bw, bh, 8, 8);
            g.setFont(Theme.font(Font.PLAIN, 11));
            g.setColor(Theme.MUTED);
            String lbl = shortTime(vals[i]);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, bx + (bw - fm.stringWidth(lbl)) / 2, bottom - bh - 5);
            g.setFont(Theme.font(i == 6 ? Font.BOLD : Font.PLAIN, 12));
            g.setColor(i == 6 ? Theme.TEXT : Theme.MUTED);
            String wd = today.minusDays(6 - i).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru"));
            fm = g.getFontMetrics();
            g.drawString(wd, bx + (bw - fm.stringWidth(wd)) / 2, bottom + 20);
        }

        // топ игр
        int rx = half + gap, rw = w - rx;
        g.setColor(Theme.CARD);
        g.fillRoundRect(rx, cy, rw, ch, 18, 18);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 15));
        g.drawString("Больше всего часов", rx + 18, cy + 30);
        List<Game> top5 = list.stream().filter(x -> x.playSeconds > 0)
                .sorted(Comparator.comparingLong((Game x) -> x.playSeconds).reversed()).limit(5).collect(Collectors.toList());
        if (top5.isEmpty()) {
            BufferedImage m = Img.get("mascot", 90);
            int my = cy + 44;
            if (m != null) {
                g.drawImage(m, rx + (rw - m.getWidth()) / 2, my, null);
                my += m.getHeight() + 6;
            } else {
                my += 40;
            }
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 13));
            FontMetrics fm = g.getFontMetrics();
            String s = "Пока пусто. Сыграй во что-нибудь";
            g.drawString(s, rx + (rw - fm.stringWidth(s)) / 2, my + 14);
        } else {
            long best = top5.get(0).playSeconds;
            int y = cy + 56;
            for (int i = 0; i < top5.size(); i++) {
                Game gm = top5.get(i);
                g.setFont(Theme.font(Font.BOLD, 14));
                FontMetrics fm = g.getFontMetrics();
                String t = Util.duration(gm.playSeconds);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 12));
                int tWidth = g.getFontMetrics().stringWidth(t);
                g.drawString(t, rx + rw - 18 - tWidth, y + 14);
                g.setColor(Theme.TEXT);
                g.setFont(Theme.font(Font.BOLD, 14));
                g.drawString(Ui.ellipsize(gm.name, fm, rw - 36 - tWidth - 12), rx + 18, y + 14);
                g.setColor(Theme.BG);
                g.fillRoundRect(rx + 18, y + 24, rw - 36, 8, 8, 8);
                g.setColor(PALETTE[i % PALETTE.length]);
                g.fillRoundRect(rx + 18, y + 24, Math.max(8, (int) ((rw - 36) * gm.playSeconds / (double) best)), 8, 8, 8);
                y += 46;
            }
        }
        g.dispose();
    }
}

/* ============================================================
 *  Главное окно
 * ============================================================ */
final class MainWindow extends JFrame {
    private static final int[] KONAMI = {KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_DOWN,
            KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_B, KeyEvent.VK_A};
    private static final String[] LOGO_PHRASES = {"Ай!", "Не щекочи", "Я лаунчер, а не кнопка",
            "Ладно, ещё разок можно", "Серьёзно?", "Ты что-то ищешь?", "Тут ничего нет. Почти", "Мне щекотно!"};
    private static final String[] TIPS = {"Двойной клик по карточке запускает игру", "Правая кнопка мыши открывает меню игры",
            "Ctrl+F — быстрый поиск", "Ctrl+N — добавить игру", "Обложку можно поменять в меню игры или перетащить картинку на карточку",
            "F11 — на весь экран", "Тыкни на логотип. Ну просто так", "Папку с играми можно просканировать целиком"};
    private static final String[] WEATHER = {"выкл", "снег", "дождь"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru"));

    private final List<Game> games = Storage.loadGames();
    private final Properties stats = Storage.loadStats();
    private final Achievements ach = new Achievements(stats);
    private final Map<String, Long> running = new LinkedHashMap<>();   // id игры -> время старта

    private final DefaultListModel<Game> model = new DefaultListModel<>();
    private final SearchField search = new SearchField("Найти игру в библиотеке");
    private final JComboBox<String> sort = new JComboBox<>(
            new String[]{"По названию", "Недавно играл", "Больше часов", "Избранные сверху"});
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel achList = new JPanel();
    private final JLabel achSummary = Ui.label("", 14, Font.PLAIN, Theme.MUTED);
    private final Bar achBar = new Bar(Theme.GOLD);
    private final JLabel tagline = Ui.label("лаунчер с приколами", 12, Font.PLAIN, Theme.MUTED);
    private final JLabel rankLabel = Ui.label("", 15, Font.BOLD, Theme.TEXT);
    private final JLabel pointsLabel = Ui.label("", 12, Font.PLAIN, Theme.MUTED);
    private final Bar rankBar = new Bar(Theme.ACCENT);
    private final JLabel statusLeft = Ui.label("", 12, Font.PLAIN, Theme.MUTED);
    private final JLabel statusRight = Ui.label("", 12, Font.PLAIN, Theme.MUTED);
    private final Mascot mascot = new Mascot();
    private final Weather weather = new Weather();
    private final StatsView statsView;
    private final Timer searchTimer;
    private JList<Game> list;
    private Btn navLib, navStats, navAch, playBtn, favBtn, delBtn, weatherBtn;
    private int konamiPos, hoverIdx = -1, tipIdx, tick;

    MainWindow() {
        super("Goodwell Play!");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                ach.inc("starts");
                checkAchievements();
            }
        });
        setIconImage(Theme.appIcon());
        setSize(1120, 760);
        setMinimumSize(new Dimension(940, 660));
        setLocationRelativeTo(null);

        statsView = new StatsView(() -> games, stats);

        ach.onUnlock = a -> {
            Storage.saveStats(stats);
            Toast.post(this, a);
            mascot.say("Есть! «" + a.title + "»");
            tick = 0;
            refreshAchievements();
            refreshSidebar();
        };

        // Засчитываем «поиск» только когда человек перестал печатать
        searchTimer = new Timer(900, e -> {
            if (search.getText().trim().length() < 2) return;
            ach.inc("searches");
            if (!games.isEmpty() && model.isEmpty()) ach.unlock("nothing");
            checkAchievements();
        });
        searchTimer.setRepeats(false);

        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        content.setBackground(Theme.BG);
        content.add(buildLibrary(), "lib");
        content.add(buildStats(), "stats");
        content.add(buildAchievements(), "ach");
        add(content, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setGlassPane(weather);
        int month = LocalDate.now().getMonthValue();
        int wm = (int) ach.getLong("weatherMode");
        applyWeather(stats.containsKey("weatherMode") ? wm : (month == 12 || month <= 2 ? 1 : 0));

        installKonami();
        installHotkeys();
        refreshList();
        refreshAchievements();
        refreshSidebar();
        updateStatus();
        greet();
        new Timer(1000, e -> {
            updateStatus();
            if (++tick % 25 == 0) nextTip();
        }).start();
    }

    /* ---------------- построение интерфейса ---------------- */

    private JComponent buildSidebar() {
        JPanel side = new GrainPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.SIDEBAR);
        side.setBorder(new EmptyBorder(24, 18, 18, 18));
        side.setPreferredSize(new Dimension(240, 0));

        Logo logo = Ui.left(new Logo());
        logo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onLogoClick();
            }
        });
        Ui.left(tagline);
        tagline.setBorder(new EmptyBorder(6, 2, 0, 0));

        navLib = nav("Библиотека", "lib", "harddrive");
        navStats = nav("Статистика", "stats", "framerate");
        navAch = nav("Достижения", "ach", "celebrate");
        navLib.setBase(Theme.CARD_HI);

        weatherBtn = new Btn("Погода: выкл", Theme.SIDEBAR, "weather", 24);
        weatherBtn.setHorizontalAlignment(SwingConstants.LEFT);
        weatherBtn.setBorder(new EmptyBorder(8, 16, 8, 16));
        weatherBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        weatherBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        weatherBtn.setToolTipText("Снег и дождь поверх окна");
        weatherBtn.addActionListener(e -> {
            int m = (weather.mode() + 1) % 3;
            applyWeather(m);
            ach.set("weatherMode", m);
            if (m != 0) ach.unlock("weather");
            Storage.saveStats(stats);
        });

        mascot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                nextTip();
            }
        });

        RoundPanel rank = new RoundPanel(Theme.CARD, 18);
        rank.setLayout(new BoxLayout(rank, BoxLayout.Y_AXIS));
        rank.setBorder(new EmptyBorder(14, 14, 14, 14));
        rank.setAlignmentX(Component.LEFT_ALIGNMENT);
        rank.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        rankLabel.setBorder(new EmptyBorder(0, 0, 3, 0));
        rank.add(rankLabel);
        rank.add(pointsLabel);
        rank.add(Box.createVerticalStrut(12));
        rank.add(rankBar);

        side.add(logo);
        side.add(tagline);
        side.add(Box.createVerticalStrut(26));
        side.add(navLib);
        side.add(Box.createVerticalStrut(6));
        side.add(navStats);
        side.add(Box.createVerticalStrut(6));
        side.add(navAch);
        side.add(Box.createVerticalGlue());
        side.add(weatherBtn);
        side.add(Box.createVerticalStrut(10));
        side.add(mascot);
        side.add(Box.createVerticalStrut(10));
        side.add(rank);
        return side;
    }

    private Btn nav(String text, String card, String icon) {
        Btn b = new Btn(text, Theme.SIDEBAR, icon, 26);
        b.setIconTextGap(12);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(9, 16, 9, 16));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        b.addActionListener(e -> showCard(card));
        return b;
    }

    private JComponent buildLibrary() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(Theme.BG);
        p.setBorder(new EmptyBorder(24, 28, 14, 28));

        search.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                refreshList();
                searchTimer.restart();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        Ui.styleCombo(sort);
        sort.addActionListener(e -> {
            refreshList();
            int mask = (int) ach.getLong("sorts") | (1 << sort.getSelectedIndex());
            ach.set("sorts", mask);
            if (mask == 15) ach.unlock("sorter");
            Storage.saveStats(stats);
        });
        Btn store = new Btn("Искать в магазине", Theme.CARD_HI, "world-map", 22);
        store.addActionListener(e -> searchInStore());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(sort);
        right.add(store);
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(search, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        list = new JList<Game>(model) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().getSize() > 0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                Ui.aa(g2);
                String[] hint = emptyHint();
                BufferedImage m = Img.get("mascot", 140);
                int cy = getHeight() / 2 + (m != null ? 40 : -20);
                if (m != null) g2.drawImage(m, (getWidth() - m.getWidth()) / 2, cy - 40 - m.getHeight(), null);
                g2.setFont(Theme.font(Font.BOLD, 21));
                g2.setColor(Theme.TEXT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint[0], (getWidth() - fm.stringWidth(hint[0])) / 2, cy);
                g2.setFont(Theme.font(Font.PLAIN, 14));
                g2.setColor(Theme.MUTED);
                fm = g2.getFontMetrics();
                g2.drawString(hint[1], (getWidth() - fm.stringWidth(hint[1])) / 2, cy + 32);
                g2.dispose();
            }
        };
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(GameCard.W + 16);
        list.setFixedCellHeight(GameCard.H + 16);
        list.setBackground(Theme.BG);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new GameCard(g -> running.containsKey(g.id), () -> hoverIdx));
        list.addListSelectionListener(e -> updateButtons());
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) launch(gameAt(e.getPoint()));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Game g = gameAt(e.getPoint());
                int i = g == null ? -1 : list.locationToIndex(e.getPoint());
                if (i != hoverIdx) {
                    hoverIdx = i;
                    list.setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    list.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverIdx != -1) {
                    hoverIdx = -1;
                    list.repaint();
                }
            }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) launch(list.getSelectedValue());
                if (e.getKeyCode() == KeyEvent.VK_DELETE && list.getSelectedValue() != null) remove(list.getSelectedValue());
            }
        });

        JScrollPane sp = Ui.scroll(list);
        TransferHandler drop = fileDropHandler();
        list.setTransferHandler(drop);
        sp.setTransferHandler(drop);
        sp.getViewport().setTransferHandler(drop);

        playBtn = new Btn("Играть", Theme.ACCENT, "rocket", 22);
        playBtn.addActionListener(e -> launch(list.getSelectedValue()));
        Btn addBtn = new Btn("Добавить игру", Theme.CARD_HI, "insert", 22);
        addBtn.addActionListener(e -> addGameDialog());
        Btn scanBtn = new Btn("Сканировать папку", Theme.CARD_HI, "magnifier", 22);
        scanBtn.addActionListener(e -> scanFolder());
        favBtn = new Btn("В избранное", Theme.CARD_HI, "shortcut", 22);
        favBtn.addActionListener(e -> {
            if (list.getSelectedValue() != null) toggleFavorite(list.getSelectedValue());
        });
        delBtn = new Btn("Удалить", Theme.DANGER, "bin", 22);
        delBtn.addActionListener(e -> {
            if (list.getSelectedValue() != null) remove(list.getSelectedValue());
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(playBtn);
        actions.add(addBtn);
        actions.add(scanBtn);
        actions.add(favBtn);
        actions.add(delBtn);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(actions, BorderLayout.WEST);

        p.add(top, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildStats() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG);
        p.setBorder(new EmptyBorder(24, 28, 14, 28));
        p.add(statsView, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildAchievements() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBackground(Theme.BG);
        p.setBorder(new EmptyBorder(24, 28, 14, 28));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        JLabel title = Ui.label("Достижения", 28, Font.BOLD, Theme.TEXT);
        Ui.left(title);
        Ui.left(achSummary);
        achSummary.setBorder(new EmptyBorder(4, 0, 12, 0));
        head.add(title);
        head.add(achSummary);
        head.add(achBar);

        achList.setLayout(new BoxLayout(achList, BoxLayout.Y_AXIS));
        achList.setOpaque(false);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG);
        wrap.add(achList, BorderLayout.NORTH);

        p.add(head, BorderLayout.NORTH);
        p.add(Ui.scroll(wrap), BorderLayout.CENTER);
        return p;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.SIDEBAR);
        bar.setBorder(new EmptyBorder(9, 22, 9, 22));
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    /* ---------------- обновление данных на экране ---------------- */

    private String[] emptyHint() {
        if (games.isEmpty()) {
            return new String[]{"Здесь пока пусто",
                    "Нажми «Добавить игру», просканируй папку или перетащи сюда .exe"};
        }
        return new String[]{"Ничего не нашлось по запросу «" + search.getText().trim() + "»",
                "Проверь написание или поищи в магазине"};
    }

    private boolean matches(Game g, String q) {
        String n = g.name.toLowerCase(Locale.ROOT);
        for (String token : q.split("\\s+")) if (!n.contains(token)) return false;
        return true;
    }

    private void refreshList() {
        if (list == null) return;
        Game selected = list.getSelectedValue();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        List<Game> view = games.stream().filter(g -> q.isEmpty() || matches(g, q)).collect(Collectors.toList());
        Comparator<Game> byName = Comparator.comparing(g -> g.name.toLowerCase(Locale.ROOT));
        switch (sort.getSelectedIndex()) {
            case 1 -> view.sort(Comparator.comparingLong((Game g) -> g.lastPlayed).reversed().thenComparing(byName));
            case 2 -> view.sort(Comparator.comparingLong((Game g) -> g.playSeconds).reversed().thenComparing(byName));
            case 3 -> view.sort(Comparator.comparing((Game g) -> !g.favorite).thenComparing(byName));
            default -> view.sort(byName);
        }
        hoverIdx = -1;
        model.clear();
        for (Game g : view) model.addElement(g);
        if (selected != null) {
            int idx = view.indexOf(selected);
            if (idx >= 0) list.setSelectedIndex(idx);
        }
        updateButtons();
        list.repaint();
    }

    private void updateButtons() {
        if (playBtn == null) return;
        Game g = list.getSelectedValue();
        playBtn.setEnabled(g != null);
        favBtn.setEnabled(g != null);
        delBtn.setEnabled(g != null);
        favBtn.setText(g != null && g.favorite ? "Убрать из избранного" : "В избранное");
    }

    private void refreshAchievements() {
        achList.removeAll();
        List<Achievement> sorted = new ArrayList<>(ach.all);
        sorted.sort(Comparator.comparing((Achievement a) -> !a.unlocked())
                .thenComparing(a -> -a.unlockedAt));
        for (Achievement a : sorted) {
            achList.add(achRow(a));
            achList.add(Box.createVerticalStrut(8));
        }
        achSummary.setText("Открыто " + ach.unlockedCount() + " из " + ach.all.size()
                + ". Набрано " + ach.points() + " GW из " + ach.maxPoints());
        achBar.setValue(ach.maxPoints() == 0 ? 0 : (double) ach.points() / ach.maxPoints());
        achList.revalidate();
        achList.repaint();
    }

    private JComponent achRow(Achievement a) {
        boolean on = a.unlocked();
        boolean hidden = a.secret && !on;

        RoundPanel row = new RoundPanel(on ? Theme.CARD : Theme.SIDEBAR, 18);
        row.setLayout(new BorderLayout(16, 0));
        row.setBorder(new EmptyBorder(12, 14, 12, 20));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        row.setPreferredSize(new Dimension(100, 76));

        RoundPanel badge = new RoundPanel(on ? new Color(255, 209, 102, 45) : Theme.BG, 26);
        badge.setLayout(new BorderLayout());
        badge.setPreferredSize(new Dimension(52, 52));
        badge.add(Ui.achIcon(a, 40, !on, hidden), BorderLayout.CENTER);
        JPanel badgeWrap = new JPanel(new GridBagLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel title = Ui.label(hidden ? "Секретное достижение" : a.title, 16, Font.BOLD, on ? Theme.TEXT : Theme.MUTED);
        JLabel desc = Ui.label(hidden ? "Попробуй сделать что-нибудь странное" : a.desc, 13, Font.PLAIN, Theme.MUTED);
        title.setBorder(new EmptyBorder(0, 0, 3, 0));
        text.add(Box.createVerticalGlue());
        text.add(title);
        text.add(desc);
        text.add(Box.createVerticalGlue());

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        JLabel pts = Ui.label("+" + a.points + " GW", 15, Font.BOLD, on ? Theme.GOLD : Theme.MUTED);
        pts.setAlignmentX(Component.RIGHT_ALIGNMENT);
        side.add(Box.createVerticalGlue());
        side.add(pts);
        if (on) {
            String date = Instant.ofEpochMilli(a.unlockedAt).atZone(ZoneId.systemDefault()).format(DATE);
            JLabel when = Ui.label(date, 12, Font.PLAIN, Theme.MUTED);
            when.setAlignmentX(Component.RIGHT_ALIGNMENT);
            side.add(when);
        }
        side.add(Box.createVerticalGlue());

        row.add(badgeWrap, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(side, BorderLayout.EAST);
        return row;
    }

    private void refreshSidebar() {
        double frac = ach.maxPoints() == 0 ? 0 : (double) ach.points() / ach.maxPoints();
        rankBar.setValue(frac);
        String rank;
        if (frac >= 1) rank = "Бог Goodwell";
        else if (frac >= 0.75) rank = "Легенда подъезда";
        else if (frac >= 0.5) rank = "Друг с играми";
        else if (frac >= 0.25) rank = "Уверенный геймер";
        else if (frac >= 0.1) rank = "Нажиматель кнопок";
        else rank = "Кожаный новичок";
        rankLabel.setText(rank);
        pointsLabel.setText(ach.points() + " GW · " + ach.unlockedCount() + " из " + ach.all.size());
    }

    private long totalSeconds() {
        return games.stream().mapToLong(g -> g.playSeconds).sum();
    }

    private void updateStatus() {
        long now = System.currentTimeMillis();
        long live = 0;
        for (Long start : running.values()) live += (now - start) / 1000;
        statsView.live = live;
        statsView.repaint();
        if (running.isEmpty()) {
            statusLeft.setText("Ничего не запущено. Выбери игру и нажми «Играть»");
        } else {
            Map.Entry<String, Long> first = running.entrySet().iterator().next();
            Game g = games.stream().filter(x -> x.id.equals(first.getKey())).findFirst().orElse(null);
            String extra = running.size() > 1 ? " и ещё " + (running.size() - 1) : "";
            statusLeft.setText("Сейчас играешь: " + (g == null ? "?" : g.name) + extra + "  ·  "
                    + Util.clock((now - first.getValue()) / 1000));
        }
        statusRight.setText(games.size() + " " + Util.plural(games.size(), "игра", "игры", "игр")
                + " · наиграно " + Util.duration(totalSeconds() + live));
    }

    private void showCard(String name) {
        cards.show(content, name);
        navLib.setBase(name.equals("lib") ? Theme.CARD_HI : Theme.SIDEBAR);
        navStats.setBase(name.equals("stats") ? Theme.CARD_HI : Theme.SIDEBAR);
        navAch.setBase(name.equals("ach") ? Theme.CARD_HI : Theme.SIDEBAR);
        if (name.equals("ach")) ach.inc("achTab");
        if (name.equals("stats")) ach.inc("statsTab");
        if (!name.equals("lib")) checkAchievements();
    }

    private void checkAchievements() {
        int favs = (int) games.stream().filter(g -> g.favorite).count();
        ach.evaluate(games.size(), favs, totalSeconds());
        Storage.saveStats(stats);
    }

    private void afterLibraryChange() {
        Storage.saveGames(games);
        refreshList();
        checkAchievements();
        updateStatus();
    }

    private void applyWeather(int m) {
        weather.setMode(m);
        weatherBtn.setText("Погода: " + WEATHER[m]);
    }

    /* ---------------- маскот ---------------- */

    private void greet() {
        int h = LocalTime.now().getHour();
        if (h < 5) mascot.say("Опять не спишь? Ну ладно, во что играем?");
        else if (h < 12) mascot.say("Доброе утро! Во что сыграем?");
        else if (h < 18) mascot.say("Привет! Что сегодня запустим?");
        else mascot.say("Добрый вечер! Самое время для игр");
    }

    private void nextTip() {
        mascot.say(TIPS[tipIdx++ % TIPS.length]);
    }

    /* ---------------- действия ---------------- */

    private Game gameAt(Point pt) {
        int i = list.locationToIndex(pt);
        if (i < 0) return null;
        Rectangle r = list.getCellBounds(i, i);
        return r != null && r.contains(pt) ? model.get(i) : null;
    }

    private void showPopup(MouseEvent e) {
        Game g = gameAt(e.getPoint());
        if (g == null) return;
        list.setSelectedValue(g, true);
        JPopupMenu m = new JPopupMenu();
        m.setBorder(new LineBorder(Theme.CARD_HI));
        m.add(menuItem("Играть", () -> launch(g)));
        m.add(menuItem(g.favorite ? "Убрать из избранного" : "В избранное", () -> toggleFavorite(g)));
        m.add(menuItem("Переименовать…", () -> rename(g)));
        m.add(menuItem("Сменить обложку…", () -> pickCover(g)));
        if (Covers.hasCustom(g)) {
            m.add(menuItem("Убрать свою обложку", () -> {
                Covers.reset(g);
                list.repaint();
            }));
        }
        if (!g.isUri()) {
            m.add(menuItem("Показать в папке", () -> {
                try {
                    File parent = new File(g.target).getParentFile();
                    if (parent != null) {
                        Desktop.getDesktop().open(parent);
                        ach.unlock("folder");
                    }
                } catch (Exception ex) {
                    error("Не получилось открыть папку", String.valueOf(ex.getMessage()));
                }
            }));
        }
        m.addSeparator();
        m.add(menuItem("Удалить из библиотеки", () -> remove(g)));
        m.show(list, e.getX(), e.getY());
        ach.unlock("ctx");
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setOpaque(true);
        mi.setBackground(Theme.CARD);
        mi.setForeground(Theme.TEXT);
        mi.setBorder(new EmptyBorder(8, 16, 8, 24));
        mi.addActionListener(e -> action.run());
        return mi;
    }

    private void toggleFavorite(Game g) {
        g.favorite = !g.favorite;
        afterLibraryChange();
    }

    private void rename(Game g) {
        String n = JOptionPane.showInputDialog(this, "Новое название", g.name);
        if (n != null && !n.isBlank()) {
            g.name = n.trim();
            afterLibraryChange();
            ach.unlock("rename");
        }
    }

    private void pickCover(Game g) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Выбери картинку для обложки");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Картинки", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (Covers.set(g, fc.getSelectedFile())) {
            list.repaint();
            ach.unlock("cover");
        } else {
            error("Не удалось поставить обложку", "Не получилось прочитать эту картинку. Попробуй PNG или JPG.");
        }
    }

    private void remove(Game g) {
        int r = JOptionPane.showConfirmDialog(this,
                "Удалить «" + g.name + "» из библиотеки?\nСама игра на диске останется на месте.",
                "Удаление", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, Img.icon("trash", 48));
        if (r != JOptionPane.YES_OPTION) return;
        games.remove(g);
        Covers.reset(g);
        ach.inc("removed");
        afterLibraryChange();
        ach.unlock("bye");
    }

    private boolean addGameSilently(String name, String target) {
        for (Game g : games) if (g.target.equalsIgnoreCase(target)) return false;
        games.add(new Game(name, target));
        return true;
    }

    private void addGameDialog() {
        JTextField name = Ui.field();
        JTextField target = Ui.field();
        Btn browse = new Btn("Обзор…", Theme.CARD_HI);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                target.setText(f.getAbsolutePath());
                if (name.getText().isBlank()) name.setText(Util.guessName(f));
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(Ui.label("Название", 13, Font.PLAIN, Theme.MUTED), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(name, c);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(Ui.label("Файл или ссылка", 13, Font.PLAIN, Theme.MUTED), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(target, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(browse, c);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(Ui.label("Путь к .exe или .jar, либо ссылка вроде steam://rungameid/730", 12, Font.PLAIN, Theme.MUTED), c);

        int r = JOptionPane.showConfirmDialog(this, form, "Добавить игру",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, Img.icon("box", 48));
        if (r != JOptionPane.OK_OPTION) return;
        String n = name.getText().trim(), t = target.getText().trim();
        if (n.isEmpty() || t.isEmpty()) {
            error("Заполни оба поля", "Нужны название игры и путь к файлу (или ссылка).");
            return;
        }
        if (addGameSilently(n, t)) {
            afterLibraryChange();
            if (Util.isUri(t)) ach.unlock("urlgame");
        } else {
            info("Уже в библиотеке", "Игра с таким путём уже добавлена.");
        }
    }

    private void scanFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Выбери папку с играми");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path root = fc.getSelectedFile().toPath();
        Set<String> known = games.stream().map(g -> g.target.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        statusLeft.setText("Сканирую " + root + "…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<GameScanner.Found>, Void>() {
            @Override
            protected List<GameScanner.Found> doInBackground() {
                return GameScanner.scan(root, known);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                updateStatus();
                try {
                    showScanResult(get());
                } catch (Exception ex) {
                    error("Сканирование не удалось", String.valueOf(ex.getMessage()));
                }
            }
        }.execute();
    }

    private void showScanResult(List<GameScanner.Found> found) {
        if (found.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Подходящих файлов в этой папке нет. Попробуй выбрать папку уровнем выше:\n"
                    + "лаунчер заглядывает на 4 уровня вглубь.", "Ничего не нашлось",
                    JOptionPane.INFORMATION_MESSAGE, Img.icon("offline", 48));
            return;
        }
        JPanel boxes = new JPanel();
        boxes.setLayout(new BoxLayout(boxes, BoxLayout.Y_AXIS));
        boxes.setBackground(Theme.BG);
        boxes.setBorder(new EmptyBorder(8, 10, 8, 10));
        List<JCheckBox> checks = new ArrayList<>();
        for (GameScanner.Found f : found) {
            JCheckBox cb = new JCheckBox(f.name() + "     " + f.path(), true);
            cb.setOpaque(false);
            cb.setForeground(Theme.TEXT);
            cb.setFont(Theme.font(Font.PLAIN, 13));
            cb.setBorder(new EmptyBorder(4, 0, 4, 0));
            checks.add(cb);
            boxes.add(cb);
        }
        JScrollPane sp = Ui.scroll(boxes);
        sp.setPreferredSize(new Dimension(680, 320));
        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setOpaque(false);
        wrap.add(Ui.label("Отметь, что добавить в библиотеку", 14, Font.PLAIN, Theme.TEXT), BorderLayout.NORTH);
        wrap.add(sp, BorderLayout.CENTER);

        int r = JOptionPane.showConfirmDialog(this, wrap, "Найдено: " + found.size(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, Img.icon("magnifier", 48));
        if (r != JOptionPane.OK_OPTION) return;
        int added = 0;
        for (int i = 0; i < found.size(); i++) {
            if (checks.get(i).isSelected() && addGameSilently(found.get(i).name(), found.get(i).path())) added++;
        }
        if (added > 0) {
            afterLibraryChange();
            ach.unlock("scan");
        }
    }

    private TransferHandler fileDropHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                try {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    int added = 0;
                    boolean covered = false;
                    for (File f : files) {
                        if (Util.isImage(f)) {
                            // картинка, брошенная на окно, становится обложкой выбранной (или карточки под курсором) игры
                            Game target = list.getSelectedValue();
                            Point pt = s.getDropLocation() == null ? null : s.getDropLocation().getDropPoint();
                            if (pt != null && s.getComponent() == list && gameAt(pt) != null) target = gameAt(pt);
                            if (target != null && Covers.set(target, f)) covered = true;
                        } else if (f.isDirectory()) {
                            Set<String> known = games.stream().map(g -> g.target.toLowerCase(Locale.ROOT))
                                    .collect(Collectors.toSet());
                            for (GameScanner.Found found : GameScanner.scan(f.toPath(), known)) {
                                if (addGameSilently(found.name(), found.path())) added++;
                            }
                        } else if (addGameSilently(Util.guessName(f), f.getAbsolutePath())) {
                            added++;
                        }
                    }
                    if (covered) {
                        list.repaint();
                        ach.unlock("cover");
                    }
                    if (added > 0) {
                        afterLibraryChange();
                        ach.unlock("dragdrop");
                    }
                    return added > 0 || covered;
                } catch (Exception ex) {
                    return false;
                }
            }
        };
    }

    private void searchInStore() {
        String q = search.getText().trim();
        Util.browse(q.isEmpty() ? "https://store.steampowered.com/"
                : "https://store.steampowered.com/search/?term=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        ach.unlock("store");
    }

    /* ---------------- запуск игр ---------------- */

    private boolean opensViaShell(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".lnk") || n.endsWith(".bat") || n.endsWith(".cmd") || n.endsWith(".url");
    }

    private Process startProcess(File f) throws IOException {
        String n = f.getName().toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();
        if (n.endsWith(".jar")) {
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
            cmd.add("-jar");
            cmd.add(f.getAbsolutePath());
        } else if (Util.OS.contains("mac") && n.endsWith(".app")) {
            cmd.add("open");
            cmd.add("-W");           // ждать закрытия, чтобы считать время
            cmd.add(f.getAbsolutePath());
        } else {
            cmd.add(f.getAbsolutePath());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(f.getParentFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    private void launch(Game g) {
        if (g == null) return;
        if (running.containsKey(g.id)) {
            info("Уже запущено", "«" + g.name + "» уже работает. Загляни в панель задач.");
            return;
        }
        Long startedAt = null;
        Process process = null;
        try {
            if (g.isUri()) {
                Desktop.getDesktop().browse(URI.create(g.target));
            } else {
                File f = new File(g.target);
                if (!f.exists()) {
                    error("Файл не найден", g.target + "\nВозможно, игру перенесли или удалили. "
                            + "Удали её из библиотеки и добавь заново.");
                    return;
                }
                if (opensViaShell(f)) {
                    Desktop.getDesktop().open(f);
                } else {
                    process = startProcess(f);
                    startedAt = System.currentTimeMillis();
                }
            }
        } catch (Exception ex) {
            error("Игра не запустилась", String.valueOf(ex.getMessage()));
            return;
        }

        if (process != null) {
            final long start = startedAt;
            running.put(g.id, start);
            if (running.size() >= 2) ach.unlock("multi");
            process.onExit().thenAccept(p -> SwingUtilities.invokeLater(() -> sessionEnded(g, start)));
        }

        g.launches++;
        g.lastPlayed = System.currentTimeMillis();
        ach.inc("launches");
        int hour = LocalTime.now().getHour();
        if (hour < 5) ach.unlock("night");
        else if (hour < 8) ach.unlock("morning");
        afterLibraryChange();
    }

    private void addDay(long sec) {
        String k = "day." + LocalDate.now();
        ach.set(k, ach.getLong(k) + sec);
    }

    private void sessionEnded(Game g, long start) {
        running.remove(g.id);
        long sec = Math.max(0, (System.currentTimeMillis() - start) / 1000);
        g.playSeconds += sec;
        addDay(sec);
        if (sec < 10) ach.unlock("short");
        if (sec >= 7200) ach.unlock("marathon");
        afterLibraryChange();
    }

    /* ---------------- пасхалки и горячие клавиши ---------------- */

    private void onLogoClick() {
        long n = ach.inc("logo");
        mascot.say(LOGO_PHRASES[(int) (n % LOGO_PHRASES.length)]);
        tick = 0;
        checkAchievements();
    }

    private void installKonami() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !isActive()) return false;
            if (e.getKeyCode() == KONAMI[konamiPos]) {
                if (++konamiPos == KONAMI.length) {
                    konamiPos = 0;
                    if (ach.unlock("konami")) mascot.say("+30 жизней");
                }
            } else {
                konamiPos = e.getKeyCode() == KONAMI[0] ? 1 : 0;
            }
            return false;
        });
    }

    private void bind(KeyStroke ks, String name, Runnable r) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void installHotkeys() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, mask), "find", () -> {
            showCard("lib");
            search.requestFocusInWindow();
            search.selectAll();
            ach.unlock("hotkey");
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, mask), "new", () -> {
            showCard("lib");
            ach.unlock("hotkey");
            addGameDialog();
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "max", () -> {
            setExtendedState(getExtendedState() == MAXIMIZED_BOTH ? NORMAL : MAXIMIZED_BOTH);
            ach.unlock("fullscreen");
        });
    }

    /* ---------------- диалоги и выход ---------------- */

    private void info(String title, String msg) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE, Img.icon("hint", 48));
    }

    private void error(String title, String msg) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.ERROR_MESSAGE, Img.icon("crash", 56));
    }

    private void shutdown() {
        long now = System.currentTimeMillis();
        if (!running.isEmpty()) ach.unlock("quit");
        for (Game g : games) {
            Long st = running.get(g.id);
            if (st != null) {   // доучитываем идущие сессии
                long sec = (now - st) / 1000;
                g.playSeconds += sec;
                addDay(sec);
            }
        }
        Storage.saveGames(games);
        Storage.saveStats(stats);
        dispose();
        System.exit(0);
    }
}
