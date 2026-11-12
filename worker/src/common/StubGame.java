import java.awt.*;
import java.awt.image.BufferedImage;

/** Stand-in game used when no jar is given; keeps the pipeline testable on its own. */
class StubGame implements Emulator {
    static final int W = 240, H = 320;
    volatile long ticks = 0;
    volatile int presses = 0;
    volatile boolean left, right;
    double bx = 120, by = 80, vx = 3, vy = 2.4, px = 100;
    final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

    StubGame() {
        Thread t = new Thread(() -> {
            int n = 0;
            while (true) {
                step();
                if (++n % 30 == 0) ticks++;
                try { Thread.sleep(33); } catch (InterruptedException e) { return; }
            }
        }, "game-loop");
        t.setDaemon(true);
        t.start();
    }

    synchronized void step() {
        bx += vx; by += vy;
        if (bx < 8 || bx > W - 8) vx = -vx;
        if (by < 8 || by > H - 30) vy = -vy;
        if (left) px = Math.max(0, px - 4);
        if (right) px = Math.min(W - 50, px + 4);
    }

    @Override public void key(boolean down, int code) {
        if (code == -3 || code == 52) left = down;
        if (code == -4 || code == 54) right = down;
        if ((code == -5 || code == 53) && down) presses++;
    }

    @Override public synchronized BufferedImage frame() {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(16, 24, 40));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(250, 204, 21));
        g.fillOval((int) bx - 8, (int) by - 8, 16, 16);
        g.setColor(new Color(56, 189, 248));
        g.fillRect((int) px, H - 20, 50, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString("TICKS: " + ticks, 10, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("5/FIRE presses: " + presses, 10, 42);
        g.drawString("4/6 or arrows move paddle", 10, 58);
        g.dispose();
        return img;
    }

    @Override public String state() { return "ticks=" + ticks + " presses=" + presses; }
}
