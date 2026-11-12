import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Structured error reports for the gateway. One line on stderr per report:
 *   ERROR fatal|warn <base64 of "title\ndetail">
 * The gateway decodes it and the web page shows it, so a game that does not start explains itself.
 */
final class Errors {
    private static final Set<String> seen = new HashSet<>();
    private static int count;

    private Errors() {}

    static void fatal(String title, Throwable t) { emit("fatal", title, describe(t)); }
    static void fatal(String title, String detail) { emit("fatal", title, detail); }
    static void warn(String title, Throwable t) { emit("warn", title, describe(t)); }
    static void warn(String title, String detail) { emit("warn", title, detail); }

    static synchronized void emit(String level, String title, String detail) {
        if (count >= 12 || !seen.add(level + title + firstLine(detail))) return; // no floods, no duplicates
        count++;
        String body = title + "\n" + detail;
        System.err.println("ERROR " + level + " " + Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    static Throwable root(Throwable t) {
        Throwable r = t;
        for (int i = 0; i < 20 && r.getCause() != null && r.getCause() != r; i++) r = r.getCause();
        return r;
    }

    /** Cause chain, a hint when the failure is a known kind, then the top of the root cause's stack trace. */
    static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable root = root(t);
        String hint = hint(root);
        if (hint != null) sb.append("Gợi ý: ").append(hint).append("\n\n");
        int depth = 0;
        for (Throwable c = t; c != null && depth < 8; c = c.getCause() == c ? null : c.getCause(), depth++) {
            sb.append(depth == 0 ? "" : "Nguyên nhân: ").append(c).append('\n');
        }
        StringWriter sw = new StringWriter();
        root.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\\R");
        sb.append('\n');
        int from = lines.length > 0 && lines[0].equals(root.toString()) ? 1 : 0; // that line is already above
        for (int i = from; i < Math.min(lines.length, 18); i++) sb.append(lines[i]).append('\n');
        if (lines.length > 18) sb.append("… (").append(lines.length - 18).append(" dòng nữa, xem log)\n");
        return sb.toString().trim();
    }

    static String hint(Throwable root) {
        String m = String.valueOf(root.getMessage());
        if (root instanceof ClassNotFoundException || root instanceof NoClassDefFoundError) {
            String c = m.replace('/', '.');
            String extra = c.startsWith("javax.microedition.m3g") ? " (đồ họa 3D M3G chưa được hỗ trợ)"
                : c.startsWith("javax.bluetooth") || c.startsWith("javax.obex") ? " (Bluetooth chưa được hỗ trợ)"
                : c.startsWith("com.mascotcapsule") ? " (Mascot Capsule 3D chưa được hỗ trợ)"
                : "";
            return "game cần lớp \"" + c + "\" mà giả lập không có" + extra + ". Có thể là API mở rộng của hãng máy hoặc jar bị thiếu file.";
        }
        if (root instanceof java.net.SocketException && m.contains("SOCKS proxy")) {
            return "mạng của giả lập đang TẮT nên game không kết nối được máy chủ. Bật bằng ALLOW_NET=1 ở gateway (xem README).";
        }
        if (root instanceof java.net.ConnectException || root instanceof java.net.UnknownHostException) {
            return "game không kết nối được tới máy chủ của nó (máy chủ tắt, bị chặn, hoặc môi trường không có mạng ra ngoài).";
        }
        if (root instanceof UnsupportedClassVersionError) {
            return "game được biên dịch cho Java mới hơn bản đang chạy.";
        }
        if (root instanceof OutOfMemoryError) {
            return "hết bộ nhớ (giới hạn hiện tại -Xmx256m).";
        }
        if (root instanceof java.util.zip.ZipException || root instanceof java.io.EOFException) {
            return "file .jar bị hỏng hoặc tải lên chưa đủ. Hãy tải lại từ nguồn gốc.";
        }
        if (root instanceof java.io.FileNotFoundException || root instanceof java.nio.file.NoSuchFileException) {
            return "thiếu file mà game cần (tài nguyên trong jar hoặc file jar không tồn tại).";
        }
        if (root instanceof ArrayIndexOutOfBoundsException) {
            for (StackTraceElement e : root.getStackTrace()) {
                if (e.getClassName().startsWith("org.objectweb.asm.ClassReader")) {
                    return "bộ đọc lớp của MicroEmulator (ASM 3.2) không đọc được một file .class trong jar. "
                        + "Thường do jar được biên dịch bằng Java quá mới (lambda, Java 8+); game J2ME gốc dùng bytecode Java 1.2–1.4.";
                }
            }
        }
        return null;
    }
}
