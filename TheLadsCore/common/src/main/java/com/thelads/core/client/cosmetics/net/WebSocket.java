package com.thelads.core.client.cosmetics.net;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A tiny RFC 6455 client: text frames, ping/pong and close only, client frames masked. No java.net.http, because 1.8.9 runs this
 * downgraded to Java 8. One reader thread calls readText(); send() is safe from any thread.
 */
public final class WebSocket implements Closeable {
    public static final int TEXT = 1, CLOSE = 8, PING = 9, PONG = 10;
    static final int MAX_IN = 256 * 1024;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final SecureRandom RANDOM = new SecureRandom();

    record Frame(int opcode, byte[] payload) {}

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private volatile boolean closed;

    private WebSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = socket.getOutputStream();
    }

    /** TCP (TLS with hostname checks for wss://), then the HTTP upgrade; every step times out after {@code timeoutMs}. */
    public static WebSocket connect(URI uri, int timeoutMs, String userAgent) throws IOException {
        boolean tls = "wss".equals(uri.getScheme());
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : tls ? 443 : 80;
        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(host, port), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            raw.setTcpNoDelay(true);
            Socket socket = raw;
            if (tls) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(params);
                ssl.startHandshake();
                socket = ssl;
            }
            WebSocket ws = new WebSocket(socket);
            ws.handshake(uri.getPort() > 0 ? host + ":" + port : host, uri.getRawPath(), userAgent);
            return ws;
        } catch (IOException | RuntimeException e) {
            raw.close();
            throw e;
        }
    }

    private void handshake(String hostHeader, String path, String userAgent) throws IOException {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        out.write(("GET " + (path == null || path.isEmpty() ? "/" : path) + " HTTP/1.1\r\nHost: " + hostHeader
            + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
            + "\r\nSec-WebSocket-Version: 13\r\nUser-Agent: " + userAgent + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        String status = line();
        if (!status.startsWith("HTTP/1.1 101")) throw new ProtocolException("upgrade refused: " + status);
        String accept = null;
        for (String header; !(header = line()).isEmpty(); ) {
            int colon = header.indexOf(':');
            if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase("Sec-WebSocket-Accept")) accept = header.substring(colon + 1).trim();
        }
        if (!accept(key).equals(accept)) throw new ProtocolException("bad Sec-WebSocket-Accept");
    }

    static String accept(String key) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.ISO_8859_1)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String line() throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c; (c = in.read()) != '\n'; ) {
            if (c < 0) throw new EOFException("handshake cut off");
            if (c != '\r') line.append((char) c);
            if (line.length() > 4096) throw new ProtocolException("handshake line too long");
        }
        return line.toString();
    }

    public void setReadTimeout(int ms) throws IOException { socket.setSoTimeout(ms); }

    public boolean isOpen() { return !closed; }

    /** The next text message, answering pings on the way; null once the server closed the connection. */
    public String readText() throws IOException {
        while (true) {
            Frame frame = decode(in, MAX_IN);
            switch (frame.opcode()) {
                case TEXT: return new String(frame.payload(), StandardCharsets.UTF_8);
                case PING: send(PONG, frame.payload()); break;
                case PONG: break;
                case CLOSE: close(); return null;
                default: throw new ProtocolException("unsupported opcode " + frame.opcode());
            }
        }
    }

    public void sendText(String text) throws IOException { send(TEXT, text.getBytes(StandardCharsets.UTF_8)); }

    public synchronized void send(int opcode, byte[] payload) throws IOException {
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        out.write(encode(opcode, payload, mask));
        out.flush();
    }

    /** Sends a normal close (1000) when still possible and drops the socket, which also ends a blocked readText(). */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            send(CLOSE, new byte[]{0x03, (byte) 0xE8});
        } catch (IOException ignored) {
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    /** One final, masked client frame. */
    public static byte[] encode(int opcode, byte[] payload, byte[] mask) {
        int n = payload.length, head = n < 126 ? 2 : n <= 0xFFFF ? 4 : 10;
        byte[] frame = new byte[head + 4 + n];
        frame[0] = (byte) (0x80 | opcode);
        if (n < 126) {
            frame[1] = (byte) (0x80 | n);
        } else if (n <= 0xFFFF) {
            frame[1] = (byte) (0x80 | 126);
            frame[2] = (byte) (n >>> 8);
            frame[3] = (byte) n;
        } else {
            frame[1] = (byte) (0x80 | 127);
            for (int i = 0; i < 8; i++) frame[2 + i] = (byte) ((long) n >>> (56 - 8 * i));
        }
        System.arraycopy(mask, 0, frame, head, 4);
        for (int i = 0; i < n; i++) frame[head + 4 + i] = (byte) (payload[i] ^ mask[i & 3]);
        return frame;
    }

    /** One server frame. Servers never mask; fragments, reserved bits and oversized frames are protocol errors (we never need them). */
    static Frame decode(DataInputStream in, int max) throws IOException {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte(), opcode = b0 & 0x0F;
        if ((b0 & 0x80) == 0 || opcode == 0) throw new ProtocolException("fragmented frame");
        if ((b0 & 0x70) != 0) throw new ProtocolException("reserved bits set");
        if ((b1 & 0x80) != 0) throw new ProtocolException("masked server frame");
        long n = b1 & 0x7F;
        if (n == 126) n = in.readUnsignedShort();
        else if (n == 127) n = in.readLong();
        if (n < 0 || n > max || (opcode >= CLOSE && n > 125)) throw new ProtocolException("frame too large: " + n);
        byte[] payload = new byte[(int) n];
        in.readFully(payload);
        return new Frame(opcode, payload);
    }
}
