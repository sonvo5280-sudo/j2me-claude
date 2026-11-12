package javax.microedition.io;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.lcdui.Host;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.TextMessage;

/**
 * Connector. Network access is OFF unless the worker was started with --net on;
 * SMS is never sent, only logged.
 */
public class Connector {
    public static final int READ = 1, WRITE = 2, READ_WRITE = 3;

    private Connector() {}

    public static Connection open(String name) throws IOException { return open(name, READ_WRITE, false); }
    public static Connection open(String name, int mode) throws IOException { return open(name, mode, false); }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name == null) throw new IllegalArgumentException("null name");
        int c = name.indexOf(':');
        if (c <= 0) throw new IllegalArgumentException("bad url: " + name);
        String scheme = name.substring(0, c).toLowerCase();
        switch (scheme) {
            case "socket": {
                if (!Host.netEnabled) throw new ConnectionNotFoundException("network disabled: " + name);
                String rest = name.substring(c + 1);
                if (!rest.startsWith("//")) throw new ConnectionNotFoundException(name);
                rest = rest.substring(2);
                int colon = rest.lastIndexOf(':');
                if (colon <= 0) throw new ConnectionNotFoundException("server sockets unsupported: " + name);
                String host = rest.substring(0, colon);
                int port = Integer.parseInt(rest.substring(colon + 1).replaceAll("[^0-9].*$", ""));
                return new Sock(host, port);
            }
            case "http":
            case "https":
                if (!Host.netEnabled) throw new ConnectionNotFoundException("network disabled: " + name);
                return new Http(name);
            case "sms":
                return new Sms(name);
            default:
                throw new ConnectionNotFoundException(name);
        }
    }

    public static InputStream openInputStream(String name) throws IOException {
        return ((InputConnection) open(name, READ)).openInputStream();
    }
    public static DataInputStream openDataInputStream(String name) throws IOException {
        return ((InputConnection) open(name, READ)).openDataInputStream();
    }
    public static OutputStream openOutputStream(String name) throws IOException {
        return ((OutputConnection) open(name, WRITE)).openOutputStream();
    }
    public static DataOutputStream openDataOutputStream(String name) throws IOException {
        return ((OutputConnection) open(name, WRITE)).openDataOutputStream();
    }

    private static final class Sock implements SocketConnection {
        private final Socket s = new Socket();

        Sock(String host, int port) throws IOException {
            s.connect(new InetSocketAddress(host, port), 10_000);
            s.setTcpNoDelay(true);
        }
        public InputStream openInputStream() throws IOException { return s.getInputStream(); }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(s.getInputStream()); }
        public OutputStream openOutputStream() throws IOException { return s.getOutputStream(); }
        public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(s.getOutputStream()); }
        public void close() throws IOException { s.close(); }
        public void setSocketOption(byte o, int v) throws IOException {
            switch (o) {
                case DELAY: s.setTcpNoDelay(v == 0); break;
                case LINGER: s.setSoLinger(v > 0, v); break;
                case KEEPALIVE: s.setKeepAlive(v != 0); break;
                case RCVBUF: s.setReceiveBufferSize(v); break;
                case SNDBUF: s.setSendBufferSize(v); break;
                default: throw new IllegalArgumentException();
            }
        }
        public int getSocketOption(byte o) throws IOException {
            switch (o) {
                case DELAY: return s.getTcpNoDelay() ? 0 : 1;
                case LINGER: return s.getSoLinger();
                case KEEPALIVE: return s.getKeepAlive() ? 1 : 0;
                case RCVBUF: return s.getReceiveBufferSize();
                case SNDBUF: return s.getSendBufferSize();
                default: throw new IllegalArgumentException();
            }
        }
        public String getLocalAddress() { return s.getLocalAddress().getHostAddress(); }
        public int getLocalPort() { return s.getLocalPort(); }
        public String getAddress() { return s.getInetAddress().getHostAddress(); }
        public int getPort() { return s.getPort(); }
    }

    private static final class Http implements HttpConnection {
        private final String url;
        private final Map<String, String> reqProps = new HashMap<>();
        private String method = GET;
        private HttpURLConnection conn;
        private ByteArrayOutputStream body;

        Http(String url) { this.url = url; }

        private HttpURLConnection conn() throws IOException {
            if (conn == null) {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);
                conn.setRequestMethod(method);
                for (Map.Entry<String, String> e : reqProps.entrySet()) conn.setRequestProperty(e.getKey(), e.getValue());
                if (body != null) {
                    conn.setDoOutput(true);
                    try (OutputStream o = conn.getOutputStream()) { o.write(body.toByteArray()); }
                }
            }
            return conn;
        }
        public String getURL() { return url; }
        public String getRequestMethod() { return method; }
        public void setRequestMethod(String m) { method = m; }
        public String getRequestProperty(String k) { return reqProps.get(k); }
        public void setRequestProperty(String k, String v) { reqProps.put(k, v); }
        public int getResponseCode() throws IOException { return conn().getResponseCode(); }
        public String getResponseMessage() throws IOException { return conn().getResponseMessage(); }
        public String getHeaderField(String n) throws IOException { return conn().getHeaderField(n); }
        public String getType() { try { return conn().getContentType(); } catch (IOException e) { return null; } }
        public String getEncoding() { try { return conn().getContentEncoding(); } catch (IOException e) { return null; } }
        public long getLength() { try { return conn().getContentLengthLong(); } catch (IOException e) { return -1; } }
        public InputStream openInputStream() throws IOException { return conn().getInputStream(); }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
        public OutputStream openOutputStream() { body = new ByteArrayOutputStream(); return body; }
        public DataOutputStream openDataOutputStream() { return new DataOutputStream(openOutputStream()); }
        public void close() { if (conn != null) conn.disconnect(); }
    }

    private static final class Sms implements MessageConnection {
        private final String url;
        Sms(String url) { this.url = url; }
        public Message newMessage(String type) { return newMessage(type, null); }
        public Message newMessage(String type, String address) {
            final String[] a = {address};
            final String[] t = {""};
            return new TextMessage() {
                public String getAddress() { return a[0]; }
                public void setAddress(String x) { a[0] = x; }
                public String getPayloadText() { return t[0]; }
                public void setPayloadText(String x) { t[0] = x; }
            };
        }
        public void send(Message m) { Host.log("SMS blocked, would have sent to " + m.getAddress() + " (" + url + ")"); }
        public void close() {}
    }
}
