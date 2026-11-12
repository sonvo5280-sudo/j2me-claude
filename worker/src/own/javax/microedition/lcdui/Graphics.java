package javax.microedition.lcdui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
    public static final int SOLID = 0, DOTTED = 1;

    private final Graphics2D g;
    private int rgb, tx, ty, strokeStyle;
    private Font font = Font.getDefaultFont();

    Graphics(BufferedImage img) {
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setClip(0, 0, img.getWidth(), img.getHeight());
        g.setFont(font.awt);
        g.setColor(Color.BLACK);
    }

    void dispose() { g.dispose(); }

    public void translate(int x, int y) { tx += x; ty += y; g.translate(x, y); }
    public int getTranslateX() { return tx; }
    public int getTranslateY() { return ty; }

    public int getColor() { return rgb; }
    public int getRedComponent() { return (rgb >> 16) & 255; }
    public int getGreenComponent() { return (rgb >> 8) & 255; }
    public int getBlueComponent() { return rgb & 255; }
    public int getGrayScale() { return (getRedComponent() + getGreenComponent() + getBlueComponent()) / 3; }
    public int getDisplayColor(int c) { return c & 0xFFFFFF; }

    public void setColor(int r, int gr, int b) {
        if (((r | gr | b) & ~255) != 0) throw new IllegalArgumentException();
        setColor((r << 16) | (gr << 8) | b);
    }

    public void setColor(int c) {
        c &= 0xFFFFFF;
        if (c != rgb || g.getColor().getAlpha() != 255) {
            rgb = c;
            g.setColor(new Color(c));
        }
    }

    public void setGrayScale(int v) {
        if (v < 0 || v > 255) throw new IllegalArgumentException();
        setColor(v, v, v);
    }

    public Font getFont() { return font; }
    public void setFont(Font f) { font = f == null ? Font.getDefaultFont() : f; g.setFont(font.awt); }

    public int getStrokeStyle() { return strokeStyle; }
    public void setStrokeStyle(int s) {
        if (s != SOLID && s != DOTTED) throw new IllegalArgumentException();
        strokeStyle = s;
        g.setStroke(s == SOLID ? new BasicStroke(1f)
                : new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{1f, 1f}, 0f));
    }

    public int getClipX() { Rectangle r = g.getClipBounds(); return r == null ? -tx : r.x; }
    public int getClipY() { Rectangle r = g.getClipBounds(); return r == null ? -ty : r.y; }
    public int getClipWidth() { Rectangle r = g.getClipBounds(); return r == null ? Host.W : r.width; }
    public int getClipHeight() { Rectangle r = g.getClipBounds(); return r == null ? Host.H : r.height; }
    public void setClip(int x, int y, int w, int h) { g.setClip(x, y, Math.max(0, w), Math.max(0, h)); }
    public void clipRect(int x, int y, int w, int h) { g.clipRect(x, y, Math.max(0, w), Math.max(0, h)); }

    public void drawLine(int x1, int y1, int x2, int y2) { g.drawLine(x1, y1, x2, y2); }
    public void drawRect(int x, int y, int w, int h) { if (w >= 0 && h >= 0) g.drawRect(x, y, w, h); }
    public void fillRect(int x, int y, int w, int h) { if (w > 0 && h > 0) g.fillRect(x, y, w, h); }
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { if (w >= 0 && h >= 0) g.drawRoundRect(x, y, w, h, aw, ah); }
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { if (w > 0 && h > 0) g.fillRoundRect(x, y, w, h, aw, ah); }
    public void drawArc(int x, int y, int w, int h, int sa, int aa) { if (w >= 0 && h >= 0) g.drawArc(x, y, w, h, sa, aa); }
    public void fillArc(int x, int y, int w, int h, int sa, int aa) { if (w > 0 && h > 0) g.fillArc(x, y, w, h, sa, aa); }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        int[] xs = {x1, x2, x3}, ys = {y1, y2, y3};
        g.fillPolygon(xs, ys, 3);
        g.drawPolygon(xs, ys, 3); // MIDP fills the edge pixels too
    }

    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) throw new NullPointerException();
        int w = font.stringWidth(s);
        int bx = (anchor & HCENTER) != 0 ? x - w / 2 : (anchor & RIGHT) != 0 ? x - w : x;
        int by = (anchor & BASELINE) != 0 ? y : (anchor & BOTTOM) != 0 ? y - font.descent() : y + font.ascent();
        g.drawString(s, bx, by);
    }

    public void drawSubstring(String s, int off, int len, int x, int y, int anchor) {
        drawString(s.substring(off, off + len), x, y, anchor);
    }

    public void drawChar(char c, int x, int y, int anchor) { drawString(String.valueOf(c), x, y, anchor); }
    public void drawChars(char[] d, int off, int len, int x, int y, int anchor) { drawString(new String(d, off, len), x, y, anchor); }

    private static int ax(int x, int w, int anchor) {
        return (anchor & HCENTER) != 0 ? x - w / 2 : (anchor & RIGHT) != 0 ? x - w : x;
    }

    private static int ay(int y, int h, int anchor) {
        return (anchor & VCENTER) != 0 ? y - h / 2 : (anchor & BOTTOM) != 0 ? y - h : y;
    }

    public void drawImage(Image im, int x, int y, int anchor) {
        if (im == null) throw new NullPointerException();
        g.drawImage(im.buf, ax(x, im.getWidth(), anchor), ay(y, im.getHeight(), anchor), null);
    }

    public void drawRegion(Image src, int xs, int ys, int w, int h, int transform, int xd, int yd, int anchor) {
        if (src == null) throw new NullPointerException();
        if (transform < 0 || transform > 7) throw new IllegalArgumentException();
        if (w < 0 || h < 0 || xs < 0 || ys < 0 || xs + w > src.getWidth() || ys + h > src.getHeight())
            throw new IllegalArgumentException();
        if (w == 0 || h == 0) return;
        boolean swap = transform >= 4;
        int dw = swap ? h : w, dh = swap ? w : h;
        int x = ax(xd, dw, anchor), y = ay(yd, dh, anchor);
        switch (transform) {
            case 0: g.drawImage(src.buf, x, y, x + w, y + h, xs, ys, xs + w, ys + h, null); break;
            case 1: g.drawImage(src.buf, x, y + h, x + w, y, xs, ys, xs + w, ys + h, null); break;
            case 2: g.drawImage(src.buf, x + w, y, x, y + h, xs, ys, xs + w, ys + h, null); break;
            case 3: g.drawImage(src.buf, x + w, y + h, x, y, xs, ys, xs + w, ys + h, null); break;
            default: g.drawImage(Image.transformed(src.buf, xs, ys, w, h, transform), x, y, null);
        }
    }

    public void drawRGB(int[] data, int off, int scan, int x, int y, int w, int h, boolean alpha) {
        if (data == null) throw new NullPointerException();
        if (w <= 0 || h <= 0) return;
        int[] px = new int[w * h];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                int p = data[off + r * scan + c];
                px[r * w + c] = alpha ? p : (p | 0xFF000000);
            }
        }
        BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        t.setRGB(0, 0, w, h, px, 0, w);
        g.drawImage(t, x, y, null);
    }

    public void copyArea(int xs, int ys, int w, int h, int xd, int yd, int anchor) {
        int x = ax(xd, w, anchor), y = ay(yd, h, anchor);
        g.copyArea(xs, ys, w, h, x - xs, y - ys);
    }
}
