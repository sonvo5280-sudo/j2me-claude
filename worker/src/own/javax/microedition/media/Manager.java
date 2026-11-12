package javax.microedition.media;

import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.control.VolumeControl;

/** Audio is not implemented yet: players keep their state machine but make no sound. */
public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";

    private Manager() {}

    public static Player createPlayer(InputStream in, String type) throws IOException, MediaException {
        if (in == null) throw new IllegalArgumentException();
        return new NullPlayer(type);
    }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        if (locator == null) throw new IllegalArgumentException();
        return new NullPlayer(locator);
    }

    public static String[] getSupportedContentTypes(String protocol) { return new String[0]; }
    public static String[] getSupportedProtocols(String type) { return new String[0]; }
    public static void playTone(int note, int duration, int volume) throws MediaException {}

    private static final class NullPlayer implements Player {
        private int state = UNREALIZED;
        private final String type;
        private final VolumeControl vol = new VolumeControl() {
            private int level = 100;
            private boolean mute;
            public void setMute(boolean m) { mute = m; }
            public boolean isMuted() { return mute; }
            public int setLevel(int l) { level = Math.max(0, Math.min(100, l)); return level; }
            public int getLevel() { return level; }
        };

        NullPlayer(String type) { this.type = type; }
        public void realize() { if (state < REALIZED) state = REALIZED; }
        public void prefetch() { if (state < PREFETCHED) state = PREFETCHED; }
        public void start() { state = STARTED; }
        public void stop() { if (state == STARTED) state = PREFETCHED; }
        public void deallocate() { if (state > REALIZED) state = REALIZED; }
        public void close() { state = CLOSED; }
        public long setMediaTime(long now) { return now; }
        public long getMediaTime() { return 0; }
        public int getState() { return state; }
        public long getDuration() { return TIME_UNKNOWN; }
        public String getContentType() { return type; }
        public void setLoopCount(int c) {}
        public void addPlayerListener(PlayerListener l) {}
        public void removePlayerListener(PlayerListener l) {}
        public Control[] getControls() { return new Control[]{vol}; }
        public Control getControl(String t) {
            return t != null && t.endsWith("VolumeControl") ? vol : null;
        }
    }
}
