package javax.microedition.lcdui;

/** Bare-bones rendering and key handling for the high-level screens (Form, TextBox, List). */
final class Screens {
    private Screens() { }

    static void paint(Displayable d, Graphics g) {
        int w = d.getWidth(), h = d.getHeight();
        Font f = Font.getDefaultFont();
        int bar = f.getHeight() + 4;
        g.setFont(f);
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);
        g.setColor(0x1E5AA8);
        g.fillRect(0, 0, w, bar);
        g.setColor(0xFFFFFF);
        g.drawString(d.getTitle() == null ? "" : d.getTitle(), 4, 2, Graphics.TOP | Graphics.LEFT);
        g.setColor(0);
        int y = bar + 4;
        if (d instanceof Form) {
            for (Object o : ((Form) d).items) {
                String s = o instanceof TextField
                        ? ((TextField) o).getLabel() + ": " + ((TextField) o).getString()
                        : String.valueOf(o);
                g.drawString(s, 4, y, Graphics.TOP | Graphics.LEFT);
                y += f.getHeight() + 2;
            }
        } else if (d instanceof TextBox) {
            g.drawString(((TextBox) d).getString(), 4, y, Graphics.TOP | Graphics.LEFT);
        } else if (d instanceof List) {
            List l = (List) d;
            for (int i = 0; i < l.entries.size(); i++) {
                if (i == l.selected) {
                    g.setColor(0xBBD5F5);
                    g.fillRect(0, y - 1, w, f.getHeight() + 2);
                    g.setColor(0);
                }
                g.drawString(l.entries.get(i), 4, y, Graphics.TOP | Graphics.LEFT);
                y += f.getHeight() + 2;
            }
        }
        g.setColor(0xDDDDDD);
        g.fillRect(0, h - bar, w, bar);
        g.setColor(0);
        if (d.commands.size() > 0) g.drawString(d.commands.get(0).getLabel(), 4, h - bar + 2, Graphics.TOP | Graphics.LEFT);
        if (d.commands.size() > 1) g.drawString(d.commands.get(1).getLabel(), w - 4, h - bar + 2, Graphics.TOP | Graphics.RIGHT);
    }

    static void key(Displayable d, int code) {
        if (d instanceof List) {
            List l = (List) d;
            if (code == -1 && l.selected > 0) l.selected--;
            else if (code == -2 && l.selected < l.entries.size() - 1) l.selected++;
            else if (code == -5 && d.listener != null) d.listener.commandAction(List.SELECT_COMMAND, d);
            return;
        }
        int idx = code == -6 ? 0 : code == -7 ? 1 : -1;
        if (idx >= 0 && idx < d.commands.size() && d.listener != null)
            d.listener.commandAction(d.commands.get(idx), d);
    }
}
