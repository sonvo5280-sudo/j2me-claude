package javax.microedition.lcdui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Glue between the MIDP implementation and the worker. Not part of MIDP. */
public final class Host {
    private Host() {}

    public static volatile int W = 240, H = 320;
    public static volatile BufferedImage screen = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
    public static final Object paintLock = new Object();
    public static volatile ClassLoader gameLoader = Host.class.getClassLoader();
    public static volatile boolean netEnabled = false;
    public static volatile boolean touch = true;
    public static volatile File rmsDir = new File("rms");
    public static final Map<String, String> props = new ConcurrentHashMap<>();
    public static volatile Runnable onExit = () -> {};
    public static final AtomicLong paints = new AtomicLong();

    /** One thread delivers key, pointer, paint and callSerially events, like a real handset. */
    public static final ExecutorService events = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "midlet-events");
        t.setDaemon(true);
        return t;
    });

    public static void init(int w, int h) {
        W = w;
        H = h;
        screen = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    public static void log(String s) {
        System.err.println("[midp] " + s);
    }

    private static volatile long lastErr;
    private static volatile int errBurst;

    public static void err(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastErr > 2000) errBurst = 0;
        lastErr = now;
        if (errBurst++ < 5) {
            System.err.println("[midp] uncaught in " + where + ": " + t);
            t.printStackTrace();
        }
    }

    /** Target of the rewritten Class.getResourceAsStream calls (see MidletLoader). */
    public static InputStream resourceOf(Class<?> c, String name) {
        if (name == null) throw new NullPointerException();
        String n;
        if (name.startsWith("/")) {
            n = name.substring(1);
        } else {
            String cn = c.getName();
            int i = cn.lastIndexOf('.');
            n = i < 0 ? name : cn.substring(0, i).replace('.', '/') + "/" + name;
        }
        return gameLoader.getResourceAsStream(n);
    }
}
