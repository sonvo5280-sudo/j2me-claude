import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Looks inside the jar before booting it, so the common mistakes get a plain explanation. */
final class Preflight {
    private Preflight() {}

    // APIs this player does not provide. A game that references them may still run if it never reaches that code.
    private static final String[][] UNSUPPORTED = {
        {"javax/microedition/m3g", "đồ họa 3D M3G (JSR-184)"},
        {"javax/bluetooth", "Bluetooth (JSR-82)"},
        {"javax/obex", "OBEX (JSR-82)"},
        {"javax/microedition/location", "định vị (JSR-179)"},
        {"javax/microedition/pim", "danh bạ/lịch (JSR-75 PIM)"},
        {"javax/microedition/amms", "đa phương tiện nâng cao (JSR-234)"},
        {"com/mascotcapsule", "Mascot Capsule 3D"},
    };

    /** Reports problems through Errors; returns false when the game cannot possibly start. */
    static boolean check(File jar, String midletOverride) {
        if (!jar.isFile()) {
            Errors.fatal("Không tìm thấy file game", "Đường dẫn: " + jar.getAbsolutePath());
            return false;
        }
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry mf = zip.getEntry("META-INF/MANIFEST.MF");
            String midlet1 = null;
            if (mf != null) {
                try (InputStream in = zip.getInputStream(mf)) {
                    midlet1 = new Manifest(in).getMainAttributes().getValue("MIDlet-1");
                }
            }
            if (midletOverride == null) {
                if (mf == null) {
                    Errors.fatal("File .jar không có MANIFEST.MF",
                        "Gợi ý: jar của game J2ME luôn có META-INF/MANIFEST.MF khai báo MIDlet-1. Có thể đây không phải jar của game.");
                    return false;
                }
                if (midlet1 == null || midlet1.trim().isEmpty()) {
                    Errors.fatal("Không biết chạy lớp nào (thiếu MIDlet-1)",
                        "MANIFEST.MF không có dòng MIDlet-1.\nGợi ý: một số game để khai báo này trong file .jad đi kèm. "
                        + "Giả lập chưa đọc .jad, hãy thêm dòng MIDlet-1 vào MANIFEST.MF của jar.");
                    return false;
                }
                String[] p = midlet1.split(",");
                String cls = p.length >= 3 ? p[2].trim() : "";
                if (cls.isEmpty()) {
                    Errors.fatal("MIDlet-1 sai định dạng", "Giá trị: \"" + midlet1 + "\"\nCần dạng: Tên, biểu tượng, lớp.Chính");
                    return false;
                }
                String path = cls.replace('.', '/') + ".class";
                if (zip.getEntry(path) == null) {
                    Errors.fatal("Jar thiếu lớp chính của game",
                        "MIDlet-1 trỏ tới \"" + cls + "\" nhưng trong jar không có " + path
                        + ".\nGợi ý: jar bị hỏng, hoặc đã bị gỡ/làm rối sai (obfuscate lỗi).");
                    return false;
                }
            }
            scanUnsupported(zip);
            return true;
        } catch (java.util.zip.ZipException e) {
            Errors.fatal("Không đọc được file .jar", Errors.describe(e));
            return false;
        } catch (Exception e) {
            Errors.fatal("Lỗi khi kiểm tra file .jar", Errors.describe(e));
            return false;
        }
    }

    private static void scanUnsupported(ZipFile zip) {
        boolean[] hit = new boolean[UNSUPPORTED.length];
        Enumeration<? extends ZipEntry> en = zip.entries();
        int scanned = 0;
        while (en.hasMoreElements() && scanned < 4000) {
            ZipEntry e = en.nextElement();
            if (!e.getName().endsWith(".class") || e.getSize() > 2_000_000) continue;
            scanned++;
            try (InputStream in = zip.getInputStream(e)) {
                String body = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                for (int i = 0; i < UNSUPPORTED.length; i++) if (!hit[i] && body.contains(UNSUPPORTED[i][0])) hit[i] = true;
            } catch (Exception ignored) { /* a bad entry is reported when the class loads */ }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < UNSUPPORTED.length; i++) if (hit[i]) sb.append("- ").append(UNSUPPORTED[i][1]).append('\n');
        if (sb.length() > 0) {
            Errors.warn("Game dùng tính năng giả lập chưa hỗ trợ",
                "Trong mã của game có tham chiếu tới:\n" + sb
                + "Game vẫn có thể chạy nếu không dùng tới phần đó, nhưng có thể treo hoặc lỗi khi vào tới đó.");
        }
    }
}
