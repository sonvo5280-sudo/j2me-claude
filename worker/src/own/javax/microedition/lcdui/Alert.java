package javax.microedition.lcdui;

public class Alert extends Screen {
    public static final int FOREVER = -2;
    private static final Command DISMISS = new Command("OK", Command.OK, 0);
    private String text;
    private Image image;
    private AlertType type;
    int timeout = 2000;
    Displayable next;

    public Alert(String title) { this(title, null, null, null); }
    public Alert(String title, String text, Image img, AlertType type) {
        this.title = title; this.text = text; this.image = img; this.type = type;
    }

    public String getString() { return text; }
    public void setString(String s) { text = s; }
    public Image getImage() { return image; }
    public void setImage(Image i) { image = i; }
    public AlertType getType() { return type; }
    public void setType(AlertType t) { type = t; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int t) { timeout = t; }
    public int getDefaultTimeout() { return 2000; }
    static Command dismiss() { return DISMISS; }
}
