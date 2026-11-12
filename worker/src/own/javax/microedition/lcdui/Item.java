package javax.microedition.lcdui;

public abstract class Item {
    public static final int PLAIN = 0, HYPERLINK = 1, BUTTON = 2;
    String label;

    Item(String label) { this.label = label; }
    public String getLabel() { return label; }
    public void setLabel(String l) { label = l; }
}
