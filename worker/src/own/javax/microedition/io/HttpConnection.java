package javax.microedition.io;

import java.io.IOException;

/** Subset of the MIDP HttpConnection API (what games actually use). */
public interface HttpConnection extends ContentConnection {
    String GET = "GET", POST = "POST", HEAD = "HEAD";
    int HTTP_OK = 200, HTTP_NOT_FOUND = 404, HTTP_INTERNAL_ERROR = 500;

    String getURL();
    String getRequestMethod();
    void setRequestMethod(String m) throws IOException;
    String getRequestProperty(String k);
    void setRequestProperty(String k, String v) throws IOException;
    int getResponseCode() throws IOException;
    String getResponseMessage() throws IOException;
    String getHeaderField(String name) throws IOException;
}
