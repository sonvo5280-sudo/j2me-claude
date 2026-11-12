package javax.microedition.lcdui;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class Font {
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int FONT_STATIC_TEXT = 0, FONT_INPUT_TEXT = 1;

    private static final Map<Integer, Font> CACHE = new HashMap<>();
    private static final Graphics2D SCRATCH = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();

    final java.awt.Font awt;
    private final int face, style, size;
    private final FontMetrics fm;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        int px = size == SIZE_SMALL ? 10 : size == SIZE_LARGE ? 15 : 12;
        int st = ((style & STYLE_BOLD) != 0 ? java.awt.Font.BOLD : 0) | ((style & STYLE_ITALIC) != 0 ? java.awt.Font.ITALIC : 0);
        awt = new java.awt.Font(face == FACE_MONOSPACE ? java.awt.Font.MONOSPACED : java.awt.Font.SANS_SERIF, st, px);
        fm = SCRATCH.getFontMetrics(awt);
    }

    public static synchronized Font getFont(int face, int style, int size) {
        int key = (face << 16) | (style << 8) | size;
        return CACHE.computeIfAbsent(key, k -> new Font(face, style, size));
    }

    public static Font getDefaultFont() { return getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM); }
    public static Font getFont(int specifier) { return getDefaultFont(); }

    public int getStyle() { return style; }
    public int getSize() { return size; }
    public int getFace() { return face; }
    public boolean isPlain() { return style == 0; }
    public boolean isBold() { return (style & STYLE_BOLD) != 0; }
    public boolean isItalic() { return (style & STYLE_ITALIC) != 0; }
    public boolean isUnderlined() { return (style & STYLE_UNDERLINED) != 0; }
    public int getHeight() { return fm.getHeight(); }
    public int getBaselinePosition() { return fm.getAscent(); }
    int ascent() { return fm.getAscent(); }
    int descent() { return fm.getDescent(); }
    public int charWidth(char ch) { return fm.charWidth(ch); }
    public int charsWidth(char[] ch, int off, int len) { return fm.charsWidth(ch, off, len); }
    public int stringWidth(String s) { return fm.stringWidth(s); }
    public int substringWidth(String s, int off, int len) { return fm.stringWidth(s.substring(off, off + len)); }
}
