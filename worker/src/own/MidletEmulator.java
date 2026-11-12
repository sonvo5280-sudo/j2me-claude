import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Host;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MidletRunner;

/** Runs a MIDlet jar on the MIDP implementation in javax.microedition.*. */
class MidletEmulator implements Emulator {
    private volatile boolean alive = true;
    private volatile boolean force = true;
    private volatile String failure;
    private long lastPaints = -1;
    private final AtomicInteger keys = new AtomicInteger();

    MidletEmulator(File jar, int w, int h, File rms, boolean net, boolean touch, String midletClass) throws Exception {
        Host.init(w, h);
        Host.rmsDir = rms;
        Host.netEnabled = net;
        Host.touch = touch;
        Host.onExit = () -> alive = false;

        String cls = midletClass;
        try (JarFile jf = new JarFile(jar)) {
            Attributes a = jf.getManifest().getMainAttributes();
            for (java.util.Map.Entry<Object, Object> e : a.entrySet()) Host.props.put(e.getKey().toString(), e.getValue().toString());
        }
        if (cls == null) {
            String m1 = Host.props.get("MIDlet-1");
            if (m1 == null) throw new IllegalArgumentException("no MIDlet-1 in manifest");
            String[] p = m1.split(",");
            cls = p[p.length - 1].trim();
        }
        System.err.println("[worker] jar=" + jar.getName() + " midlet=" + cls + " screen=" + w + "x" + h + " net=" + net + " touch=" + touch);

        System.setProperty("microedition.platform", "j2me-web");
        System.setProperty("microedition.encoding", "UTF-8");
        System.setProperty("microedition.configuration", "CLDC-1.1");
        System.setProperty("microedition.profiles", "MIDP-2.0");
        System.setProperty("microedition.locale", "vi-VN");
        System.setProperty("microedition.media.version", "1.0");
        System.setProperty("supports.mixing", "false");

        MidletLoader loader = new MidletLoader(jar.toURI().toURL(), MidletEmulator.class.getClassLoader());
        Host.gameLoader = loader;
        final String mainClass = cls;

        Thread boot = new Thread(() -> {
            try {
                Class<?> c = Class.forName(mainClass, true, loader);
                Constructor<?> k = c.getDeclaredConstructor();
                k.setAccessible(true);
                MidletRunner.start((MIDlet) k.newInstance());
                System.err.println("[worker] startApp returned");
            } catch (Throwable t) {
                Throwable root = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                failure = root.toString();
                System.err.println("[worker] MIDlet failed to start: " + root);
                root.printStackTrace();
            }
        }, "midlet-main");
        boot.start();
    }

    @Override public void key(boolean down, int code) { keys.incrementAndGet(); Display.key(down, code); }
    @Override public void pointer(int type, int x, int y) { Display.pointer(type, x, y); }
    @Override public void text(String s) { Display.text(s); }
    @Override public void invalidate() { force = true; }
    @Override public boolean alive() { return alive; }

    @Override public BufferedImage frame() {
        String f = failure;
        if (f != null && Host.paints.get() == 0) return errorFrame(f);
        long p = Host.paints.get();
        if (p == lastPaints && !force) return null;
        lastPaints = p;
        force = false;
        BufferedImage src = Host.screen;
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        synchronized (Host.paintLock) {
            Graphics2D g = copy.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }
        return copy;
    }

    private BufferedImage errorFrame(String msg) {
        BufferedImage b = new BufferedImage(Host.W, Host.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(40, 10, 10));
        g.fillRect(0, 0, b.getWidth(), b.getHeight());
        g.setColor(Color.WHITE);
        g.drawString("MIDlet failed to start:", 8, 20);
        int y = 40;
        for (int i = 0; i < msg.length(); i += 34) {
            g.drawString(msg.substring(i, Math.min(msg.length(), i + 34)), 8, y);
            y += 14;
        }
        g.dispose();
        return b;
    }

    @Override public String state() {
        return "paints=" + Host.paints.get() + " keys=" + keys.get() + " alive=" + (alive ? 1 : 0);
    }
}
