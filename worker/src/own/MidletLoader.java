import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Loads MIDlet classes from the game jar and rewrites a few CLDC idioms on the way in,
 * the same trick MicroEmulator uses. Currently:
 *   Class.getResourceAsStream(String)  ->  Host.resourceOf(Class, String)
 * (CLDC games often call "".getClass().getResourceAsStream(path), which on a normal JVM
 * would look in the bootstrap loader instead of the game jar.)
 *
 * Needs: --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
 */
final class MidletLoader extends URLClassLoader {
    MidletLoader(URL jar, ClassLoader parent) {
        super(new URL[]{jar}, parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = getResourceAsStream(path)) {
            if (in == null) throw new ClassNotFoundException(name);
            byte[] b = rewrite(in.readAllBytes());
            return defineClass(name, b, 0, b.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    static byte[] rewrite(byte[] cls) {
        ClassReader cr = new ClassReader(cls);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")
                                && n.equals("getResourceAsStream")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "javax/microedition/lcdui/Host",
                                    "resourceOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;", false);
                        } else {
                            super.visitMethodInsn(op, owner, n, d, itf);
                        }
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
