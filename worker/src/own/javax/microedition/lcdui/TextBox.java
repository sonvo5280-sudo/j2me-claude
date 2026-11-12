package javax.microedition.lcdui;

public class TextBox extends Screen {
    final TextField field;

    public TextBox(String title, String text, int maxSize, int constraints) {
        this.title = title;
        field = new TextField(null, text, maxSize, constraints);
    }

    public String getString() { return field.getString(); }
    public void setString(String s) { field.setString(s); }
    public int size() { return field.size(); }
    public int getMaxSize() { return field.getMaxSize(); }
    public int setMaxSize(int m) { return field.setMaxSize(m); }
    public int getConstraints() { return field.getConstraints(); }
    public void setConstraints(int c) { field.setConstraints(c); }
    public int getChars(char[] d) { return field.getChars(d); }
    public void setChars(char[] d, int off, int len) { field.setChars(d, off, len); }
    public void insert(String s, int pos) { field.insert(s, pos); }
    public void delete(int off, int len) { field.delete(off, len); }
}
