package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

public abstract class Displayable {
    String title;
    final List<Command> commands = new ArrayList<>();
    CommandListener listener;

    Displayable() {}

    public int getWidth() { return Host.W; }
    public int getHeight() { return Host.H; }
    public boolean isShown() { return Display.current == this; }
    public void setTitle(String t) { title = t; }
    public String getTitle() { return title; }

    public void addCommand(Command c) {
        if (c == null) throw new NullPointerException();
        synchronized (commands) { if (!commands.contains(c)) commands.add(c); }
    }

    public void removeCommand(Command c) { synchronized (commands) { commands.remove(c); } }
    public void setCommandListener(CommandListener l) { listener = l; }
    protected void sizeChanged(int w, int h) {}

    void fire(Command c) {
        CommandListener l = listener;
        if (l != null) l.commandAction(c, this);
    }
}
