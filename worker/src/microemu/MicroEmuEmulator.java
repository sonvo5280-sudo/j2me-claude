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
                failure = t.toString();
                System.err.println("[worker] MicroEmulator failed to start: " + t);
                t.printStackTrace();
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
        g.setColor(new Color(40, 10, 10));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.drawString("MicroEmulator failed to start:", 8, 20);
        int y = 40;
        for (int i = 0; i < msg.length(); i += 34) {
            g.drawString(msg.substring(i, Math.min(msg.length(), i + 34)), 8, y);
            y += 14;
        }
        g.dispose();
        return b;
    }

    @Override public String state() {
        return "paints=" + display.paints.get() + " keys=" + keys.get() + " editing=" + (editing() != null ? 1 : 0) + " alive=1";
    }
}
