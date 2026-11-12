package javax.microedition.lcdui;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.midlet.MIDlet;

public class Display {
    public static final int LIST_ELEMENT = 1, CHOICE_GROUP_ELEMENT = 2, ALERT = 3;
    public static final int COLOR_BACKGROUND = 0, COLOR_FOREGROUND = 1, COLOR_HIGHLIGHTED_BACKGROUND = 2,
            COLOR_HIGHLIGHTED_FOREGROUND = 3, COLOR_BORDER = 4, COLOR_HIGHLIGHTED_BORDER = 5;

    static volatile Display instance;
    static volatile Displayable current;

    private Display() {}

    public static Display getDisplay(MIDlet m) {
        if (m == null) throw new NullPointerException();
        synchronized (Display.class) {
            if (instance == null) instance = new Display();
            return instance;
        }
    }

    public Displayable getCurrent() { return current; }

    public void setCurrent(Displayable d) {
        if (d == null) return;
        final Displayable old;
        synchronized (Display.class) { old = current; current = d; }
        if (old != d) {
            Host.events.execute(() -> {
                try {
                    if (old instanceof Canvas) ((Canvas) old).hideNotify();
                    if (d instanceof Canvas) ((Canvas) d).showNotify();
                } catch (Throwable t) { Host.err("show/hideNotify", t); }
            });
        }
        schedulePaint();
    }

    public void setCurrent(final Alert a, final Displayable next) {
        if (a == null || next == null) throw new NullPointerException();
        a.next = next;
        setCurrent(a);
        if (a.timeout > 0) {
            Thread t = new Thread(() -> {
                try { Thread.sleep(a.timeout); } catch (InterruptedException e) { return; }
                if (current == a) setCurrent(next);
            }, "alert-timeout");
            t.setDaemon(true);
            t.start();
        }
    }

    public void setCurrentItem(Item item) {}

    public void callSerially(final Runnable r) {
        Host.events.execute(() -> { try { r.run(); } catch (Throwable t) { Host.err("callSerially", t); } });
    }

    public boolean isColor() { return true; }
    public int numColors() { return 1 << 24; }
    public int numAlphaLevels() { return 256; }
    public int getColor(int c) {
        switch (c) {
            case COLOR_BACKGROUND: return 0xFFFFFF;
            case COLOR_HIGHLIGHTED_BACKGROUND: return 0x1E3A8A;
            case COLOR_HIGHLIGHTED_FOREGROUND: return 0xFFFFFF;
            default: return 0;
        }
    }
    public int getBorderStyle(boolean highlighted) { return Graphics.SOLID; }
    public boolean flashBackLight(int ms) { return true; }
    public boolean vibrate(int ms) { return true; }
    public int getBestImageWidth(int t) { return 32; }
    public int getBestImageHeight(int t) { return 32; }

    // ---- internals used by the worker ----

    private static final AtomicBoolean needs = new AtomicBoolean(), queued = new AtomicBoolean();

    static void schedulePaint() {
        needs.set(true);
        if (queued.compareAndSet(false, true)) {
            Host.events.execute(() -> {
                queued.set(false);
                if (needs.getAndSet(false)) paintNow();
            });
        }
    }

    static void serviceRepaints() {
        if (needs.getAndSet(false)) paintNow();
    }

    static void paintNow() {
        synchronized (Host.paintLock) {
            Displayable d = current;
            if (d == null) return;
            Graphics g = new Graphics(Host.screen);
            try {
                if (d instanceof Canvas) ((Canvas) d).paint(g);
                else Skin.draw(d, g);
            } catch (Throwable t) {
                Host.err("paint", t);
            } finally {
                g.dispose();
                Host.paints.incrementAndGet();
            }
        }
    }

    public static void key(final boolean down, final int code) {
        Host.events.execute(() -> {
            try {
                Displayable d = current;
                if (d instanceof Canvas) {
                    if (down) ((Canvas) d).keyPressed(code); else ((Canvas) d).keyReleased(code);
                } else if (d != null && down) {
                    Skin.key(d, code);
                    schedulePaint();
                }
            } catch (Throwable t) { Host.err("key", t); }
        });
    }

    /** type: 0 pressed, 1 released, 2 dragged */
    public static void pointer(final int type, final int x, final int y) {
        Host.events.execute(() -> {
            try {
                Displayable d = current;
                if (d instanceof Canvas) {
                    Canvas c = (Canvas) d;
                    if (type == 0) c.pointerPressed(x, y);
                    else if (type == 1) c.pointerReleased(x, y);
                    else c.pointerDragged(x, y);
                }
            } catch (Throwable t) { Host.err("pointer", t); }
        });
    }

    /** Typed text from the web client goes into the focused TextBox / TextField. */
    public static void text(final String s) {
        Host.events.execute(() -> {
            Displayable d = current;
            if (d != null && Skin.type(d, s)) schedulePaint();
        });
    }
}
