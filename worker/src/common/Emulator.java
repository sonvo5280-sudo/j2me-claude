import java.awt.image.BufferedImage;

/** Boundary between the worker protocol and whatever runs the game. */
public interface Emulator {
    void key(boolean down, int code);

    /** type: 0 pressed, 1 released, 2 dragged; x,y in screen pixels */
    default void pointer(int type, int x, int y) {}

    default void text(String s) {}

    /** The next frame to send, or null if nothing changed since the last call. */
    BufferedImage frame();

    /** Force frame() to return an image even if nothing changed (a viewer just attached). */
    default void invalidate() {}

    /** "k=v k=v" with numeric values only; sent to the gateway once per second. */
    String state();

    default boolean alive() { return true; }

    /** True when alive() is false because the game could not start (as opposed to a normal exit). */
    default boolean failed() { return false; }
}
