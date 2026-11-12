package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    public static final int UP = 1, LEFT = 2, RIGHT = 5, DOWN = 6, FIRE = 8, GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;
    public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52, KEY_NUM5 = 53,
            KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57, KEY_STAR = 42, KEY_POUND = 35;

    protected Canvas() {}

    public boolean isDoubleBuffered() { return true; }
    public boolean hasPointerEvents() { return Host.touch; }
    public boolean hasPointerMotionEvents() { return Host.touch; }
    public boolean hasRepeatEvents() { return false; }

    public int getKeyCode(int ga) {
        switch (ga) {
            case UP: return -1;
            case DOWN: return -2;
            case LEFT: return -3;
            case RIGHT: return -4;
            case FIRE: return -5;
            case GAME_A: return KEY_NUM1;
            case GAME_B: return KEY_NUM3;
            case GAME_C: return KEY_NUM7;
            case GAME_D: return KEY_NUM9;
            default: throw new IllegalArgumentException();
        }
    }

    public String getKeyName(int kc) {
        switch (kc) {
            case -1: return "UP";
            case -2: return "DOWN";
            case -3: return "LEFT";
            case -4: return "RIGHT";
            case -5: return "SELECT";
            case -6: return "SOFT1";
            case -7: return "SOFT2";
            case 42: return "*";
            case 35: return "#";
            default: return kc >= 48 && kc <= 57 ? String.valueOf((char) kc) : "KEY" + kc;
        }
    }

    public int getGameAction(int kc) {
        switch (kc) {
            case -1: case KEY_NUM2: return UP;
            case -2: case KEY_NUM8: return DOWN;
            case -3: case KEY_NUM4: return LEFT;
            case -4: case KEY_NUM6: return RIGHT;
            case -5: case KEY_NUM5: return FIRE;
            case KEY_NUM1: return GAME_A;
            case KEY_NUM3: return GAME_B;
            case KEY_NUM7: return GAME_C;
            case KEY_NUM9: return GAME_D;
            default: return 0;
        }
    }

    protected void keyPressed(int keyCode) {}
    protected void keyRepeated(int keyCode) {}
    protected void keyReleased(int keyCode) {}
    protected void pointerPressed(int x, int y) {}
    protected void pointerReleased(int x, int y) {}
    protected void pointerDragged(int x, int y) {}
    protected void showNotify() {}
    protected void hideNotify() {}
    protected abstract void paint(Graphics g);

    public final void repaint() { Display.schedulePaint(); }
    public final void repaint(int x, int y, int w, int h) { Display.schedulePaint(); }
    public final void serviceRepaints() { Display.serviceRepaints(); }
    public void setFullScreenMode(boolean mode) {}
}
