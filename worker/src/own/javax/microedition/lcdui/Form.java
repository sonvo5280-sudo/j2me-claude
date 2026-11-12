package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

public class Form extends Screen {
    final List<Item> items = new ArrayList<>();
    int focus;

    public Form(String title) { this.title = title; }
    public Form(String title, Item[] its) {
        this.title = title;
        if (its != null) for (Item i : its) items.add(i);
    }

    public int append(Item i) { if (i == null) throw new NullPointerException(); items.add(i); return items.size() - 1; }
    public int append(String s) { return append(new StringItem(null, s)); }
    public void insert(int idx, Item i) { items.add(idx, i); }
    public void delete(int idx) { items.remove(idx); }
    public void deleteAll() { items.clear(); }
    public void set(int idx, Item i) { items.set(idx, i); }
    public Item get(int idx) { return items.get(idx); }
    public int size() { return items.size(); }
}
