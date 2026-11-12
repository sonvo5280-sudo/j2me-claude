package javax.microedition.lcdui;

import java.util.ArrayList;

public class List extends Screen implements Choice {
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);
    final java.util.List<String> items = new ArrayList<>();
    final int type;
    int selected;

    public List(String title, int listType) { this.title = title; this.type = listType; }
    public List(String title, int listType, String[] els, Image[] imgs) {
        this(title, listType);
        if (els != null) for (String s : els) items.add(s);
    }

    public int size() { return items.size(); }
    public String getString(int i) { return items.get(i); }
    public int append(String s, Image img) { items.add(s); return items.size() - 1; }
    public void insert(int i, String s, Image img) { items.add(i, s); }
    public void set(int i, String s, Image img) { items.set(i, s); }
    public void delete(int i) { items.remove(i); if (selected >= items.size()) selected = Math.max(0, items.size() - 1); }
    public void deleteAll() { items.clear(); selected = 0; }
    public int getSelectedIndex() { return items.isEmpty() ? -1 : selected; }
    public void setSelectedIndex(int i, boolean sel) { if (sel) selected = i; }
    public boolean isSelected(int i) { return i == selected; }
}
