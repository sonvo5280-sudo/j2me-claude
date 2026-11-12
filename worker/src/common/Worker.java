import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.concurrent.*;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Session worker: one JVM per play session.
 *
 * Arguments: [--jar game.jar] [--w 240] [--h 320] [--rms dir] [--net on|off] [--touch on|off]
 *            [--core own|microemu] [--midlet pkg.Class] [--snapshot out.png --after ms [--script "ms:CMD;ms:CMD"]]
 *
 * stdin  (text lines): "K <1|0> <keycode>"   key down/up
 *                      "P <0|1|2> <x> <y>"    pointer pressed/released/dragged
 *                      "T <text>"             typed text (\b = backspace)
 *                      "W <1|0>"              someone is watching / nobody is
 *                      "Q"                    quit
 * stdout (binary)    : [uint32 length][JPEG bytes] frames, only while watched
 * stderr (text)      : "STATE k=v ..." once per second, plus logs
 *
 * The game keeps running whether or not anyone watches; only frame encoding stops.
 */
public class Worker {
    static byte[] jpeg(BufferedImage im) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter w = it.next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.8f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(im, null, null), p);
        } finally {
            w.dispose();
        }
        return bos.toByteArray();
    }

    static String opt(String[] a, String name, String def) {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals(name)) return a[i + 1];
        return def;
    }

    static boolean handle(Emulator emu, boolean[] watching, String line) {
        String[] p = line.trim().split(" ", 4);
        try {
            switch (p[0]) {
                case "K": if (p.length == 3) emu.key(p[1].equals("1"), Integer.parseInt(p[2])); break;
                case "P": if (p.length == 4) emu.pointer(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])); break;
                case "T": if (line.length() > 2) emu.text(line.substring(2).replace("\\b", "\b")); break;
                case "W":
                    if (p.length == 2) {
                        boolean w = p[1].equals("1");
                        synchronized (watching) { watching[0] = w; }
                        if (w) emu.invalidate();
                    }
                    break;
                case "Q": return false;
                default: break;
            }
        } catch (NumberFormatException ignored) { }
        return true;
    }

    /**
     * Game networking is off by default. Routing every socket through a SOCKS proxy that does not exist makes
     * connect() fail at once, which the game reports as "server unreachable". This is a convenience, not a sandbox:
     * isolation of untrusted jars has to come from the container the worker runs in.
     */
    static void denyNetwork() {
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "1");
        System.setProperty("socksNonProxyHosts", "");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "1");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "1");
        System.setProperty("http.nonProxyHosts", "");
    }

    /** "own" = javax.microedition.* written for this project, "microemu" = the MicroEmulator core. */
    static Emulator createEmulator(String core, String jar, int w, int h, File rms, boolean net, boolean touch, String midlet)
            throws Exception {
        String cls = core.equals("microemu") ? "MicroEmuEmulator" : "MidletEmulator";
        java.lang.reflect.Constructor<?> c = Class.forName(cls).getDeclaredConstructor(
                File.class, int.class, int.class, File.class, boolean.class, boolean.class, String.class);
        c.setAccessible(true);
        try {
            return (Emulator) c.newInstance(new File(jar), w, h, rms, net, touch, midlet);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        final PrintStream frames = new PrintStream(new FileOutputStream(FileDescriptor.out), false);
        System.setOut(System.err); // keep stdout clean for binary frames

        String jar = opt(args, "--jar", null);
        int w = Integer.parseInt(opt(args, "--w", "240"));
        int h = Integer.parseInt(opt(args, "--h", "320"));
        boolean net = opt(args, "--net", "off").equals("on");
        if (!net) denyNetwork();
        final Emulator emu = jar == null ? new StubGame() : createEmulator(opt(args, "--core", "own"), jar, w, h,
                new File(opt(args, "--rms", "rms")), net, opt(args, "--touch", "on").equals("on"), opt(args, "--midlet", null));

        final boolean[] watching = {false};

        String snap = opt(args, "--snapshot", null);
        if (snap != null) { // debugging mode: run, optionally inject input, write one PNG, exit
            String script = opt(args, "--script", "");
            long start = System.currentTimeMillis();
            long after = Long.parseLong(opt(args, "--after", "3000"));
            String[] steps = script.isEmpty() ? new String[0] : script.split(";");
            int next = 0;
            while (System.currentTimeMillis() - start < after) {
                while (next < steps.length) {
                    int c = steps[next].indexOf(':');
                    long at = Long.parseLong(steps[next].substring(0, c));
                    if (System.currentTimeMillis() - start < at) break;
                    handle(emu, watching, steps[next].substring(c + 1));
                    next++;
                }
                Thread.sleep(20);
            }
            emu.invalidate();
            BufferedImage f = emu.frame();
            ImageIO.write(f, "png", new File(snap));
            System.err.println("STATE " + emu.state());
            System.exit(0);
        }

        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(frames));
        ScheduledExecutorService ex = Executors.newScheduledThreadPool(1);
        ex.scheduleAtFixedRate(() -> {
            try {
                boolean on;
                synchronized (watching) { on = watching[0]; }
                if (!on) return; // nobody watching: skip encoding, game keeps running
                BufferedImage f = emu.frame();
                if (f == null) return; // unchanged since last frame
                byte[] b = jpeg(f);
                out.writeInt(b.length);
                out.write(b);
                out.flush();
            } catch (Exception e) {
                System.err.println("frame error: " + e);
            }
        }, 0, 66, TimeUnit.MILLISECONDS);

        ex.scheduleAtFixedRate(() -> {
            System.err.println("STATE " + emu.state());
            if (!emu.alive()) { System.err.println("[worker] game exited"); System.exit(0); }
        }, 1, 1, TimeUnit.SECONDS);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
            if (!handle(emu, watching, line)) break;
        }
        ex.shutdownNow();
        System.exit(0);
    }
}
