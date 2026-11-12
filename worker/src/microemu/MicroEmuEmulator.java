import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.microedition.lcdui.Displayable;
import org.microemu.DisplayAccess;
import org.microemu.DisplayComponent;
import org.microemu.MIDletAccess;
import org.microemu.MIDletBridge;
import org.microemu.app.Common;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.Device;
import org.microemu.device.DeviceDisplay;
import org.microemu.device.DeviceFactory;
import org.microemu.device.EmulatorContext;
import org.microemu.device.FontManager;
import org.microemu.device.InputMethod;
import org.microemu.device.InputMethodEvent;
import org.microemu.device.InputMethodListener;
import org.microemu.device.impl.ButtonName;
import org.microemu.device.j2se.J2SEButton;
import org.microemu.device.impl.DeviceImpl;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.j2se.J2SEFontManager;
import org.microemu.device.j2se.J2SEGraphicsSurface;
import org.microemu.device.j2se.J2SEInputMethod;
import org.microemu.log.Logger;
import org.microemu.log.LoggerAppender;
import org.microemu.log.LoggingEvent;

/**
 * Runs a MIDlet jar on the real MicroEmulator core (org.microemu.*), headless:
 * a DisplayComponent that keeps the rendered surface instead of showing it in a window.
 * Same constructor as MidletEmulator so Worker can pick either core by name.
 */
class MicroEmuEmulator implements Emulator {
    private final Capture display = new Capture();
    private final ExecutorService input = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "microemu-input");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger keys = new AtomicInteger();
    private final int w, h;
    private final J2SEInputMethod im = new J2SEInputMethod();
    private final java.lang.reflect.Field listenerField;
    private volatile String failure;
    private volatile boolean force = true;
    private long lastPaints = -1;

    /** Takes the repaint requests MicroEmulator would send to its Swing window. */
    private static final class Capture implements DisplayComponent {
        volatile J2SEGraphicsSurface surface;
        final AtomicLong paints = new AtomicLong();

        @Override public void repaintRequest(int x, int y, int width, int height) {
            MIDletAccess ma = MIDletBridge.getMIDletAccess();
            if (ma == null) return;
            DisplayAccess da = ma.getDisplayAccess();
            if (da == null) return;
            Displayable current = da.getCurrent();
            if (current == null) return;
            Device device = DeviceFactory.getDevice();
            if (device == null) return;
            if (surface == null) {
                surface = new J2SEGraphicsSurface(device.getDeviceDisplay().getFullWidth(),
                        device.getDeviceDisplay().getFullHeight(), false, 0x000000);
            }
            J2SEDeviceDisplay dd = (J2SEDeviceDisplay) device.getDeviceDisplay();
            synchronized (surface) {
                dd.paintDisplayable(surface, x, y, width, height);
                if (!dd.isFullScreenMode()) dd.paintControls(surface.getGraphics());
            }
            paints.incrementAndGet();
        }
    }

    MicroEmuEmulator(File jar, int w, int h, File rms, boolean net, boolean touch, String midletClass) throws Exception {
        this.w = w;
        this.h = h;
        // lets us see whether a lcdui TextBox/TextField is being edited (MicroEmulator keeps the listener protected)
        java.lang.reflect.Field lf = null;
        try {
            lf = InputMethod.class.getDeclaredField("inputMethodListener");
            lf.setAccessible(true);
        } catch (Exception e) {
            System.err.println("[microemu] cannot inspect input method: " + e);
        }
        this.listenerField = lf;
        // MicroEmulator keeps its record stores under $user.home/.microemulator/<suite>
        rms.mkdirs();
        System.setProperty("user.home", rms.getAbsolutePath());

        final EmulatorContext ctx = new EmulatorContext() {
            private final InputMethod inputMethod = im;
            private final DeviceDisplay deviceDisplay = new J2SEDeviceDisplay(this);
            private final FontManager fontManager = new J2SEFontManager();

            public DisplayComponent getDisplayComponent() { return display; }
            public InputMethod getDeviceInputMethod() { return inputMethod; }
            public DeviceDisplay getDeviceDisplay() { return deviceDisplay; }
            public FontManager getDeviceFontManager() { return fontManager; }
            public InputStream getResourceAsStream(Class origClass, String name) {
                return MIDletBridge.getCurrentMIDlet().getClass().getResourceAsStream(name);
            }
            public boolean platformRequest(String url) {
                System.err.println("[microemu] platformRequest ignored: " + url);
                return false;
            }
        };

        // MicroEmulator swallows an exception that ends a game thread ("MIDletThread throws", debug level),
        // which is how a game can freeze without any message. Surface those and its error-level reports.
        try {
            java.lang.reflect.Field dbg = org.microemu.app.util.MIDletThread.class.getDeclaredField("debug");
            dbg.setAccessible(true);
            dbg.setBoolean(null, true);
        } catch (Exception ex) {
            System.err.println("[microemu] cannot enable thread error reports: " + ex);
        }
        Logger.addAppender(new LoggerAppender() {
            @Override public void append(LoggingEvent e) {
                Throwable t = e.getThrowable();
                if (t == null || String.valueOf(e.getMessage()).startsWith("Creating thread with no MIDlet context")) return;
                boolean thread = e.getLevel() == LoggingEvent.DEBUG && String.valueOf(e.getMessage()).startsWith("MIDletThread throws");
                if (thread) Errors.warn("Một luồng của game bị dừng vì lỗi", t);
                else if (e.getLevel() == LoggingEvent.ERROR) Errors.warn("Game gặp lỗi: " + Errors.root(t).getClass().getSimpleName(), t);
            }
        });

        System.err.println("[worker] core=microemu jar=" + jar.getName() + " screen=" + w + "x" + h + " net=" + net);

        Thread boot = new Thread(() -> {
            try {
                Common emulator = new Common(ctx);
                List<String> params = new ArrayList<>();
                params.add("--rms"); params.add("file");
                params.add("--resizableDevice"); params.add(String.valueOf(w)); params.add(String.valueOf(h));
                params.add("--quit");
                params.add(midletClass != null ? midletClass : jar.getAbsolutePath());
                DeviceEntry defaultDevice = new DeviceEntry("Default device", null, DeviceImpl.DEFAULT_LOCATION, true, false);
                emulator.initParams(params, defaultDevice, J2SEDevice.class);
                emulator.initMIDlet(true);
                System.err.println("[worker] initMIDlet returned");
            } catch (Throwable t) {
                failure = Errors.root(t).toString();
                System.err.println("[worker] MicroEmulator failed to start: " + t);
                t.printStackTrace();
                Errors.fatal("Game không khởi chạy được", t);
            }
        }, "midlet-main");
        boot.start();
    }

    private DisplayAccess access() {
        MIDletAccess ma = MIDletBridge.getMIDletAccess();
        return ma == null ? null : ma.getDisplayAccess();
    }

    /** The game's own lcdui TextBox/TextField is open (not a text field the game draws itself on a Canvas). */
    private InputMethodListener editing() {
        try {
            return listenerField == null ? null : (InputMethodListener) listenerField.get(im);
        } catch (Exception e) {
            return null;
        }
    }

    private static ButtonName nameOf(int code) {
        switch (code) {
            case -1: return ButtonName.UP;
            case -2: return ButtonName.DOWN;
            case -3: return ButtonName.LEFT;
            case -4: return ButtonName.RIGHT;
            case -5: return ButtonName.SELECT;
            case -6: return ButtonName.SOFT1;
            case -7: return ButtonName.SOFT2;
            default: return null;
        }
    }

    private J2SEButton buttonFor(int code) {
        ButtonName n = nameOf(code);
        if (n == null) return null;
        java.util.Vector<?> all = DeviceFactory.getDevice().getButtons();
        for (Object o : all) {
            if (o instanceof J2SEButton && ((J2SEButton) o).getFunctionalName() == n) return (J2SEButton) o;
        }
        return null;
    }

    /**
     * Keys while a TextBox is open: printable codes are inserted as text (no multi-tap, digits stay digits),
     * Backspace (-8 / 8) deletes, the rest goes through MicroEmulator's own button path (caret, soft-key commands).
     */
    private void editKey(InputMethodListener l, boolean down, int code, DisplayAccess da) {
        if (code >= 32) {
            if (down) im.clipboardPaste(String.valueOf((char) code));
        } else if (code == -8 || code == 8) {
            if (!down) return;
            String t = l.getText();
            int caret = l.getCaretPosition();
            if (t != null && caret > 0 && caret <= t.length()) {
                String n = t.substring(0, caret - 1) + t.substring(caret);
                l.inputMethodTextChanged(new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, caret - 1, n));
                l.caretPositionChanged(new InputMethodEvent(InputMethodEvent.CARET_POSITION_CHANGED, caret - 1, n));
            }
        } else {
            J2SEButton b = buttonFor(code);
            if (b != null) {
                if (down) im.buttonPressed(b, '\0'); else im.buttonReleased(b, '\0');
            } else if (down) da.keyPressed(code); else da.keyReleased(code);
        }
    }

    @Override public void key(boolean down, int code) {
        keys.incrementAndGet();
        input.execute(() -> {
            DisplayAccess da = access();
            if (da == null) return;
            InputMethodListener l = editing();
            if (l != null) { editKey(l, down, code, da); return; }
            if (down) da.keyPressed(code); else da.keyReleased(code);
        });
    }

    @Override public void pointer(int type, int x, int y) {
        input.execute(() -> {
            DisplayAccess da = access();
            if (da == null) return;
            if (type == 0) da.pointerPressed(x, y);
            else if (type == 1) da.pointerReleased(x, y);
            else da.pointerDragged(x, y);
        });
    }

    @Override public void text(String s) {
        input.execute(() -> {
            DisplayAccess da = access();
            if (da == null || s == null) return;
            if (editing() != null) { im.clipboardPaste(s); return; }
            for (char c : s.toCharArray()) { da.keyPressed(c); da.keyReleased(c); } // canvas: the game reads key codes
        });
    }

    @Override public boolean alive() { return failure == null; }
    @Override public boolean failed() { return failure != null; }

    @Override public void invalidate() { force = true; }

    @Override public BufferedImage frame() {
        String f = failure;
        J2SEGraphicsSurface s = display.surface;
        if (f != null && s == null) return errorFrame(f);
        if (s == null) return null;
        long p = display.paints.get();
        if (p == lastPaints && !force) return null;
        lastPaints = p;
        force = false;
        BufferedImage src = s.getImage();
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        synchronized (s) {
            Graphics2D g = copy.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }
        return copy;
    }

    private BufferedImage errorFrame(String msg) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(60, 12, 12));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        java.awt.FontMetrics fm = g.getFontMetrics();
        int y = 18;
        g.drawString("Game khong khoi chay duoc", 8, y);
        y += 18;
        g.setColor(new Color(255, 200, 200));
        StringBuilder line = new StringBuilder();
        for (String word : msg.split(" ")) {
            while (fm.stringWidth(word) > w - 16 && word.length() > 1) { // break very long class names
                int n = word.length();
                while (n > 1 && fm.stringWidth(word.substring(0, n)) > w - 16) n--;
                if (line.length() > 0) { g.drawString(line.toString(), 8, y); y += 14; line.setLength(0); }
                g.drawString(word.substring(0, n), 8, y); y += 14;
                word = word.substring(n);
            }
            if (fm.stringWidth(line + " " + word) > w - 16 && line.length() > 0) {
                g.drawString(line.toString(), 8, y); y += 14; line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) { g.drawString(line.toString(), 8, y); y += 14; }
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Chi tiet: xem log cua phien.", 8, Math.max(y + 10, h - 10));
        g.dispose();
        return b;
    }

    @Override public String state() {
        return "paints=" + display.paints.get() + " keys=" + keys.get() + " editing=" + (editing() != null ? 1 : 0) + " alive=1";
    }
}
