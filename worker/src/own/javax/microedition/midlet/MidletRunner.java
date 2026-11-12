package javax.microedition.midlet;

/** Lets the worker call the protected lifecycle methods (same package). Not part of MIDP. */
public final class MidletRunner {
    private MidletRunner() { }

    public static void start(MIDlet m) throws Exception { m.startApp(); }
    public static void pause(MIDlet m) { m.pauseApp(); }
    public static void destroy(MIDlet m) throws Exception { m.destroyApp(true); }
}
