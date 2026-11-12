package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Minimal renderer and key handling for the high-level screens (Form, List, TextBox, Alert). */
final class Skin {
    private Skin() {}

    private static boolean negative(Command c) {
        int t = c.getCommandType();
        return t == Command.BACK || t == Command.CANCEL || t == Command.EXIT || t == Command.STOP;
    }

    /** {left, right} soft key commands */
    static Command[] softKeys(Displayable d) {
        List<Command> cs;
        synchronized (d.commands) { cs = new ArrayList<>(d.commands); }
        cs.sort(Comparator.comparingInt(Command::getPriority));
        Command left = null, right = null;
        for (Command c : cs) {
            if (!negative(c) && left == null) left = c;
            else if (negative(c) && right == null) right = c;
        }
        if (left == null) for (Command c : cs) if (c != right) { left = c; break; }
        if (right == null) for (Command c : cs) if (c != left) { right = c; break; }
        return new Command[]{left, right};
    }

    private static List<String> wrap(String s, Font f, int maxW) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String para : s.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (f.stringWidth(trial) <= maxW || cur.length() == 0) cur = new StringBuilder(trial);
                else { out.add(cur.toString()); cur = new StringBuilder(word); }
            }
            out.add(cur.toString());
        }
        return out;
    }

    static void draw(Displayable d, Graphics g) {
        int W = Host.W, H = Host.H;
        Font f = Font.getDefaultFont();
        g.setFont(f);
        int bar = f.getHeight() + 6;
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, W, H);
        g.setColor(0x1E3A8A);
        g.fillRect(0, 0, W, bar);
        g.setColor(0xFFFFFF);
        g.drawString(d.title == null ? "" : d.title, 4, 3, Graphics.TOP | Graphics.LEFT);

        int y = bar + 6, maxW = W - 8;
        g.setColor(0x111827);
        if (d instanceof TextBox) {
            y = field(g, f, ((TextBox) d).field, null, y, maxW, true);
        } else if (d instanceof Form) {
            Form fm = (Form) d;
            for (int i = 0; i < fm.items.size(); i++) {
                Item it = fm.items.get(i);
                if (it instanceof TextField) {
                    y = field(g, f, (TextField) it, it.getLabel(), y, maxW, i == fm.focus);
                } else if (it instanceof StringItem) {
                    StringItem si = (StringItem) it;
                    String t = (si.getLabel() != null ? si.getLabel() + " " : "") + (si.getText() == null ? "" : si.getText());
                    for (String line : wrap(t, f, maxW)) { g.drawString(line, 4, y, Graphics.TOP | Graphics.LEFT); y += f.getHeight(); }
                    y += 4;
                }
            }
        } else if (d instanceof List) {
            List2 l = new List2((javax.microedition.lcdui.List) d);
            for (int i = 0; i < l.items.size(); i++) {
                boolean sel = i == l.selected;
                if (sel) { g.setColor(0x1E3A8A); g.fillRect(0, y - 1, W, f.getHeight() + 2); g.setColor(0xFFFFFF); }
                else g.setColor(0x111827);
                g.drawString(l.items.get(i), 6, y, Graphics.TOP | Graphics.LEFT);
                y += f.getHeight() + 2;
            }
        } else if (d instanceof Alert) {
            for (String line : wrap(((Alert) d).getString(), f, maxW)) {
                g.drawString(line, 4, y, Graphics.TOP | Graphics.LEFT);
                y += f.getHeight();
            }
        }

        g.setColor(0xE5E7EB);
        g.fillRect(0, H - bar, W, bar);
        g.setColor(0x111827);
        Command[] sk = softKeys(d);
        if (sk[0] != null) g.drawString(sk[0].getLabel(), 4, H - bar + 3, Graphics.TOP | Graphics.LEFT);
        if (sk[1] != null) g.drawString(sk[1].getLabel(), W - 4, H - bar + 3, Graphics.TOP | Graphics.RIGHT);
    }

    /** thin view over List so Skin can read package-private fields without a name clash with java.util.List */
    private static final class List2 {
        final java.util.List<String> items;
        final int selected;
        List2(javax.microedition.lcdui.List l) { items = l.items; selected = l.selected; }
    }

    private static int field(Graphics g, Font f, TextField tf, String label, int y, int maxW, boolean focus) {
        if (label != null && label.length() > 0) { g.setColor(0x374151); g.drawString(label, 4, y, Graphics.TOP | Graphics.LEFT); y += f.getHeight() + 2; }
        String shown = tf.getString();
        if ((tf.getConstraints() & TextField.PASSWORD) != 0) shown = "*".repeat(shown.length());
        int h = f.getHeight() + 6;
        g.setColor(0xFFFFFF);
        g.fillRect(4, y, maxW, h);
        g.setColor(focus ? 0x2563EB : 0x9CA3AF);
        g.drawRect(4, y, maxW, h);
        g.setColor(0x111827);
        while (shown.length() > 0 && f.stringWidth(shown) > maxW - 8) shown = shown.substring(1);
        g.drawString(shown + (focus ? "|" : ""), 8, y + 3, Graphics.TOP | Graphics.LEFT);
        return y + h + 6;
    }

    static void key(Displayable d, int code) {
        if (d instanceof Alert) {
            Alert a = (Alert) d;
            if (a.next != null) Display.instance.setCurrent(a.next);
            return;
        }
        Command[] sk = softKeys(d);
        if (code == -6 && sk[0] != null) { d.fire(sk[0]); return; }
        if (code == -7 && sk[1] != null) { d.fire(sk[1]); return; }
        if (d instanceof javax.microedition.lcdui.List) {
            javax.microedition.lcdui.List l = (javax.microedition.lcdui.List) d;
            if (code == -1 && l.selected > 0) l.selected--;
            else if (code == -2 && l.selected < l.items.size() - 1) l.selected++;
            else if (code == -5) {
                if (l.listener != null) l.fire(javax.microedition.lcdui.List.SELECT_COMMAND);
                else if (sk[0] != null) d.fire(sk[0]);
            }
        } else if (d instanceof Form) {
            Form f = (Form) d;
            if (code == -1 && f.focus > 0) f.focus--;
            else if (code == -2 && f.focus < f.items.size() - 1) f.focus++;
        }
    }

    static boolean type(Displayable d, String s) {
        if (d instanceof TextBox) return ((TextBox) d).field.accept(s);
        if (d instanceof Form) {
            Form f = (Form) d;
            if (f.focus < f.items.size() && f.items.get(f.focus) instanceof TextField)
                return ((TextField) f.items.get(f.focus)).accept(s);
        }
        return false;
    }
}
