package javax.microedition.midlet;

import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.lcdui.Host;

public abstract class MIDlet {
    protected MIDlet() {}

    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() { Host.log("MIDlet.notifyDestroyed"); Host.onExit.run(); }
    public final void notifyPaused() {}
    public final void resumeRequest() {}
    public final String getAppProperty(String key) { return Host.props.get(key); }

    public final boolean platformRequest(String url) throws ConnectionNotFoundException {
        Host.log("platformRequest ignored: " + url);
        return false;
    }

    public final int checkPermission(String permission) { return 1; }
}
