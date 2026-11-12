package javax.microedition.lcdui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class Image {
    final BufferedImage buf;
    private final boolean mutable;

    private Image(BufferedImage b, boolean mutable) {
        this.buf = b;
        this.mutable = mutable;
    }

    public static Image createImage(int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return new Image(b, true);
    }

    public static Image createImage(Image src) {
        if (src == null) throw new NullPointerException();
        if (!src.mutable) return src;
        return new Image(copy(src.buf), false);
    }

    public static Image createImage(String name) throws IOException {
        if (name == null) throw new NullPointerException();
        InputStream in = Host.resourceOf(Image.class, name.startsWith("/") ? name : "/" + name);
        if (in == null) throw new IOException("resource not found: " + name);
        try (InputStream i = in) {
            return createImage(i);
        }
    }

    public static Image createImage(InputStream in) throws IOException {
        if (in == null) throw new NullPointerException();
        return createImage(in.readAllBytes(), 0, Integer.MAX_VALUE);
    }

    public static Image createImage(byte[] data, int off, int len) {
        if (data == null) throw new NullPointerException();
        if (off < 0 || len < 0 || off > data.length) throw new ArrayIndexOutOfBoundsException();
        len = Math.min(len, data.length - off);
        try {
            BufferedImage b = ImageIO.read(new ByteArrayInputStream(data, off, len));
            if (b == null) throw new IllegalArgumentException("unsupported image format");
            return new Image(copy(b), false);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    public static Image createImage(Image src, int x, int y, int w, int h, int transform) {
        if (src == null) throw new NullPointerException();
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > src.getWidth() || y + h > src.getHeight()
                || transform < 0 || transform > 7) throw new IllegalArgumentException();
        return new Image(transformed(src.buf, x, y, w, h, transform), false);
    }

    public static Image createRGBImage(int[] rgb, int w, int h, boolean processAlpha) {
        if (rgb == null) throw new NullPointerException();
        if (w <= 0 || h <= 0) throw new IllegalArgumentException();
        if (rgb.length < w * h) throw new ArrayIndexOutOfBoundsException();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] dst = new int[w * h];
        for (int i = 0; i < dst.length; i++) dst[i] = processAlpha ? rgb[i] : (rgb[i] | 0xFF000000);
        b.setRGB(0, 0, w, h, dst, 0, w);
        return new Image(b, false);
    }

    public Graphics getGraphics() {
        if (!mutable) throw new IllegalStateException("image is immutable");
        return new Graphics(buf);
    }

    public boolean isMutable() { return mutable; }
    public int getWidth() { return buf.getWidth(); }
    public int getHeight() { return buf.getHeight(); }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int w, int h) {
        if (rgbData == null) throw new NullPointerException();
        if (x < 0 || y < 0 || x + w > getWidth() || y + h > getHeight()) throw new IllegalArgumentException();
        if (w <= 0 || h <= 0) return;
        buf.getRGB(x, y, w, h, rgbData, offset, scanlength);
    }

    private static BufferedImage copy(BufferedImage s) {
        BufferedImage b = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(s, 0, 0, null);
        g.dispose();
        return b;
    }

    /** Region of src with a MIDP Sprite transform applied (TRANS_* constants 0..7). */
    static BufferedImage transformed(BufferedImage src, int x, int y, int w, int h, int t) {
        boolean swap = t >= 4;
        int dw = swap ? h : w, dh = swap ? w : h;
        int[] in = new int[w * h];
        src.getRGB(x, y, w, h, in, 0, w);
        int[] out = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            for (int dx = 0; dx < dw; dx++) {
                int sx, sy;
                switch (t) {
                    case 1: sx = dx; sy = h - 1 - dy; break;            // MIRROR_ROT180
                    case 2: sx = w - 1 - dx; sy = dy; break;            // MIRROR
                    case 3: sx = w - 1 - dx; sy = h - 1 - dy; break;    // ROT180
                    case 4: sx = dy; sy = dx; break;                    // MIRROR_ROT270
                    case 5: sx = dy; sy = h - 1 - dx; break;            // ROT90
                    case 6: sx = w - 1 - dy; sy = dx; break;            // ROT270
                    case 7: sx = w - 1 - dy; sy = h - 1 - dx; break;    // MIRROR_ROT90
                    default: sx = dx; sy = dy;
                }
                out[dy * dw + dx] = in[sy * w + sx];
            }
        }
        BufferedImage b = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, dw, dh, out, 0, dw);
        return b;
    }
}
