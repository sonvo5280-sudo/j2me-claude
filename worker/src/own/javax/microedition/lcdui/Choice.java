package javax.microedition.lcdui;

public interface Choice {
    int EXCLUSIVE = 1, MULTIPLE = 2, IMPLICIT = 3, POPUP = 4;
    int TEXT_WRAP_DEFAULT = 0, TEXT_WRAP_ON = 1, TEXT_WRAP_OFF = 2;

    int size();
    String getString(int i);
    int append(String s, Image img);
    void delete(int i);
    void deleteAll();
    int getSelectedIndex();
    void setSelectedIndex(int i, boolean sel);
    boolean isSelected(int i);
}
