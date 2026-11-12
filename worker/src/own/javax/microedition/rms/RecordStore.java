package javax.microedition.rms;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import javax.microedition.lcdui.Host;

/** File-backed record store. One file per store under Host.rmsDir, rewritten on every change. */
public class RecordStore {
    private static final Map<String, RecordStore> OPEN = new HashMap<>();
    private static final int CAPACITY = 4 * 1024 * 1024;

    private final String name;
    private final File file;
    private final TreeMap<Integer, byte[]> recs = new TreeMap<>();
    private int nextId = 1, version = 0, refs = 1;
    private long lastModified = System.currentTimeMillis();
    private boolean open = true;

    private RecordStore(String name, File file) { this.name = name; this.file = file; }

    private static File fileFor(String n) {
        try { return new File(Host.rmsDir, URLEncoder.encode(n, "UTF-8") + ".rs"); }
        catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    public static synchronized RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        if (name == null || name.length() == 0 || name.length() > 32) throw new IllegalArgumentException("bad name");
        RecordStore rs = OPEN.get(name);
        if (rs != null) { rs.refs++; return rs; }
        File f = fileFor(name);
        rs = new RecordStore(name, f);
        if (f.exists()) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
                rs.nextId = in.readInt();
                rs.version = in.readInt();
                int n = in.readInt();
                for (int i = 0; i < n; i++) {
                    int id = in.readInt();
                    byte[] b = new byte[in.readInt()];
                    in.readFully(b);
                    rs.recs.put(id, b);
                }
            } catch (IOException e) { throw new RecordStoreException(e.toString()); }
        } else {
            if (!create) throw new RecordStoreNotFoundException(name);
            rs.persist();
        }
        OPEN.put(name, rs);
        return rs;
    }

    public static synchronized void deleteRecordStore(String name) throws RecordStoreException {
        if (OPEN.containsKey(name)) throw new RecordStoreException("store is open");
        File f = fileFor(name);
        if (!f.exists()) throw new RecordStoreNotFoundException(name);
        f.delete();
    }

    public static synchronized String[] listRecordStores() {
        File[] fs = Host.rmsDir.listFiles((d, n) -> n.endsWith(".rs"));
        if (fs == null || fs.length == 0) return null;
        String[] out = new String[fs.length];
        try {
            for (int i = 0; i < fs.length; i++) {
                String n = fs[i].getName();
                out[i] = URLDecoder.decode(n.substring(0, n.length() - 3), "UTF-8");
            }
        } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
        return out;
    }

    private void persist() throws RecordStoreException {
        try {
            Host.rmsDir.mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                out.writeInt(nextId);
                out.writeInt(version);
                out.writeInt(recs.size());
                for (Map.Entry<Integer, byte[]> e : recs.entrySet()) {
                    out.writeInt(e.getKey());
                    out.writeInt(e.getValue().length);
                    out.write(e.getValue());
                }
            }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file); }
        } catch (IOException e) { throw new RecordStoreException(e.toString()); }
    }

    private void check() throws RecordStoreNotOpenException {
        if (!open) throw new RecordStoreNotOpenException(name);
    }

    public synchronized void closeRecordStore() throws RecordStoreException {
        check();
        synchronized (RecordStore.class) {
            if (--refs <= 0) { open = false; OPEN.remove(name); }
        }
    }

    public String getName() throws RecordStoreNotOpenException { check(); return name; }
    public synchronized int getVersion() throws RecordStoreNotOpenException { check(); return version; }
    public synchronized int getNumRecords() throws RecordStoreNotOpenException { check(); return recs.size(); }
    public synchronized long getLastModified() throws RecordStoreNotOpenException { check(); return lastModified; }
    public synchronized int getNextRecordID() throws RecordStoreNotOpenException { check(); return nextId; }

    public synchronized int getSize() throws RecordStoreNotOpenException {
        check();
        int s = 0;
        for (byte[] b : recs.values()) s += b.length;
        return s;
    }

    public int getSizeAvailable() throws RecordStoreNotOpenException { return Math.max(0, CAPACITY - getSize()); }

    public synchronized int addRecord(byte[] data, int off, int num) throws RecordStoreException {
        check();
        byte[] b = slice(data, off, num);
        if (getSize() + b.length > CAPACITY) throw new RecordStoreFullException();
        int id = nextId++;
        recs.put(id, b);
        touch();
        return id;
    }

    public synchronized void setRecord(int id, byte[] data, int off, int num) throws RecordStoreException {
        check();
        if (!recs.containsKey(id)) throw new InvalidRecordIDException("id " + id);
        recs.put(id, slice(data, off, num));
        touch();
    }

    public synchronized void deleteRecord(int id) throws RecordStoreException {
        check();
        if (recs.remove(id) == null) throw new InvalidRecordIDException("id " + id);
        touch();
    }

    public synchronized int getRecordSize(int id) throws RecordStoreException {
        check();
        byte[] b = recs.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        return b.length;
    }

    public synchronized byte[] getRecord(int id) throws RecordStoreException {
        check();
        byte[] b = recs.get(id);
        if (b == null) throw new InvalidRecordIDException("id " + id);
        return b.clone();
    }

    public synchronized int getRecord(int id, byte[] buf, int off) throws RecordStoreException {
        byte[] b = getRecord(id);
        if (off < 0 || off + b.length > buf.length) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(b, 0, buf, off, b.length);
        return b.length;
    }

    public synchronized RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator cmp, boolean keepUpdated)
            throws RecordStoreNotOpenException {
        check();
        List<Integer> ids = new ArrayList<>();
        for (Map.Entry<Integer, byte[]> e : recs.entrySet())
            if (filter == null || filter.matches(e.getValue().clone())) ids.add(e.getKey());
        if (cmp != null) ids.sort((a, b) -> cmp.compare(recs.get(a), recs.get(b)));
        return new Enum(ids);
    }

    public void addRecordListener(RecordListener l) {}
    public void removeRecordListener(RecordListener l) {}

    private void touch() throws RecordStoreException {
        version++;
        lastModified = System.currentTimeMillis();
        persist();
    }

    private static byte[] slice(byte[] d, int off, int n) {
        if (d == null) return new byte[0];
        if (off < 0 || n < 0 || off + n > d.length) throw new ArrayIndexOutOfBoundsException();
        return Arrays.copyOfRange(d, off, off + n);
    }

    private final class Enum implements RecordEnumeration {
        private final List<Integer> ids;
        private int pos = 0;
        Enum(List<Integer> ids) { this.ids = ids; }
        public int numRecords() { return ids.size(); }
        public byte[] nextRecord() throws RecordStoreException { return getRecord(nextRecordId()); }
        public int nextRecordId() throws InvalidRecordIDException {
            if (pos >= ids.size()) throw new InvalidRecordIDException();
            return ids.get(pos++);
        }
        public byte[] previousRecord() throws RecordStoreException { return getRecord(previousRecordId()); }
        public int previousRecordId() throws InvalidRecordIDException {
            if (pos <= 0) throw new InvalidRecordIDException();
            return ids.get(--pos);
        }
        public boolean hasNextElement() { return pos < ids.size(); }
        public boolean hasPreviousElement() { return pos > 0; }
        public void reset() { pos = 0; }
        public void rebuild() {}
        public void keepUpdated(boolean k) {}
        public boolean isKeptUpdated() { return false; }
        public void destroy() {}
    }
}
