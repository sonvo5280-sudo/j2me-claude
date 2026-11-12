package javax.microedition.lcdui;

public class TextField extends Item {
    public static final int ANY = 0, EMAILADDR = 1, NUMERIC = 2, PHONENUMBER = 3, URL = 4, DECIMAL = 5;
    public static final int PASSWORD = 0x10000, UNEDITABLE = 0x20000, SENSITIVE = 0x40000, NON_PREDICTIVE = 0x80000,
            INITIAL_CAPS_WORD = 0x100000, INITIAL_CAPS_SENTENCE = 0x200000, CONSTRAINT_MASK = 0xFFFF;

    private StringBuilder text = new StringBuilder();
    private int maxSize, constraints;

    public TextField(String label, String text, int maxSize, int constraints) {
        super(label);
        if (maxSize <= 0) throw new IllegalArgumentException();
        this.maxSize = maxSize;
        this.constraints = constraints;
        setString(text);
    }

    public String getString() { return text.toString(); }
    public void setString(String s) {
        text = new StringBuilder(s == null ? "" : s.length() > maxSize ? s.substring(0, maxSize) : s);
    }
    public int size() { return text.length(); }
    public int getMaxSize() { return maxSize; }
    public int setMaxSize(int m) {
        if (m <= 0) throw new IllegalArgumentException();
        maxSize = m;
        if (text.length() > m) text.setLength(m);
        return m;
    }
    public int getConstraints() { return constraints; }
    public void setConstraints(int c) { constraints = c; }
    public int getChars(char[] d) { text.getChars(0, text.length(), d, 0); return text.length(); }
    public void setChars(char[] d, int off, int len) { setString(new String(d, off, len)); }
    public void insert(String s, int pos) { text.insert(pos, s); if (text.length() > maxSize) text.setLength(maxSize); }
    public void delete(int off, int len) { text.delete(off, off + len); }

    boolean accept(String s) {
        if ((constraints & UNEDITABLE) != 0) return false;
        boolean changed = false;
        for (char c : s.toCharArray()) {
            if (c == '\b') {
                if (text.length() > 0) { text.setLength(text.length() - 1); changed = true; }
            } else if (text.length() < maxSize && (c >= ' ')) {
                if ((constraints & CONSTRAINT_MASK) == NUMERIC && !Character.isDigit(c)) continue;
                text.append(c);
                changed = true;
            }
        }
        return changed;
    }
}
