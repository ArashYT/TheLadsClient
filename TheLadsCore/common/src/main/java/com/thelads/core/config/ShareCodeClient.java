package com.thelads.core.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ShareCodeClient {
    public static final String BASE_URL = "https://bot.arashyt.ca";
    public static final int CONNECT_TIMEOUT_MS = 5000;
    public static final int READ_TIMEOUT_MS = 5000;
    public static final String USER_AGENT = "TheLadsClient/1.9.4";
    public static final int MAX_RESPONSE_BYTES = 32 * 1024; // 32 KB cap

    public static class ShareNotFoundException extends IOException {
        public ShareNotFoundException(String message) {
            super(message);
        }
    }

    public static class ShareServerException extends IOException {
        private final int statusCode;
        public ShareServerException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
        public int getStatusCode() {
            return statusCode;
        }
    }

    public interface Transport {
        Response send(String method, String urlString, String contentType, byte[] body) throws IOException;
    }

    public static class Response {
        private final int statusCode;
        private final byte[] body;

        public Response(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body != null ? body : new byte[0];
        }

        public int getStatusCode() {
            return statusCode;
        }

        public byte[] getBody() {
            return body;
        }

        public String getBodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public static class DefaultTransport implements Transport {
        @Override
        public Response send(String method, String urlString, String contentType, byte[] body) throws IOException {
            if (!urlString.startsWith("https://")) {
                throw new IllegalArgumentException("HTTPS only: " + urlString);
            }
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            if (contentType != null) {
                conn.setRequestProperty("Content-Type", contentType);
            }
            if (body != null && body.length > 0) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                conn.disconnect();
                return new Response(code, new byte[0]);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int read;
            int total = 0;
            try {
                while ((read = is.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) {
                        throw new IOException("Response exceeds 32 KB limit");
                    }
                    baos.write(buf, 0, read);
                }
            } finally {
                try { is.close(); } catch (Exception ignored) {}
                conn.disconnect();
            }
            return new Response(code, baos.toByteArray());
        }
    }

    private static Transport transport = new DefaultTransport();

    public static void setTransport(Transport t) {
        transport = t != null ? t : new DefaultTransport();
    }

    public static Transport getTransport() {
        return transport;
    }

    public static String normalizeShortCode(String input) {
        if (input == null) return null;
        String s = input.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("LADS-")) {
            s = s.substring(5).trim();
        }
        // Server alphabet: 23456789ABCDEFGHJKMNPQRSTUVWXYZ
        if (s.length() == 6 && s.matches("^[2-9A-HJ-NP-Z]{6}$")) {
            return s;
        }
        // Also accept general 6-char alphanumeric if pasted
        if (s.length() == 6 && s.matches("^[A-Z0-9]{6}$")) {
            return s;
        }
        return null;
    }

    public static boolean isShortCode(String input) {
        return normalizeShortCode(input) != null;
    }

    /**
     * POSTs the long code to https://bot.arashyt.ca/share and returns "LADS-XXXXXX".
     * Throws IOException on error.
     */
    public static String postShare(String longCode) throws IOException {
        if (longCode == null || !longCode.startsWith("LADS1:")) {
            throw new IllegalArgumentException("Payload must be a valid LADS1: code");
        }
        JsonObject body = new JsonObject();
        body.addProperty("payload", longCode);
        byte[] payloadBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        Response resp = transport.send("POST", BASE_URL + "/share", "application/json; charset=UTF-8", payloadBytes);
        int status = resp.getStatusCode();
        if (status != 200) {
            throw new ShareServerException(status, "Share server returned HTTP " + status);
        }
        String respText = resp.getBodyAsString();
        try {
            JsonObject json = JsonParser.parseString(respText).getAsJsonObject();
            if (json.has("code") && json.get("code").isJsonPrimitive()) {
                String code = json.get("code").getAsString().trim().toUpperCase(Locale.ROOT);
                return "LADS-" + code;
            }
            throw new IOException("Invalid server response: missing 'code'");
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to parse share response", e);
        }
    }

    /**
     * GETs the payload from https://bot.arashyt.ca/share/<code>.
     * Returns the long payload string (e.g. "LADS1:...").
     * Throws ShareNotFoundException on 404, or IOException on network/server errors.
     */
    public static String getShare(String shortCode) throws IOException {
        String normalized = normalizeShortCode(shortCode);
        if (normalized == null) {
            throw new IllegalArgumentException("Invalid short code: " + shortCode);
        }
        Response resp = transport.send("GET", BASE_URL + "/share/" + normalized, null, null);
        int status = resp.getStatusCode();
        if (status == 404) {
            throw new ShareNotFoundException("Code not found");
        }
        if (status != 200) {
            throw new ShareServerException(status, "Can't reach the share server (HTTP " + status + ")");
        }
        String respText = resp.getBodyAsString();
        try {
            JsonObject json = JsonParser.parseString(respText).getAsJsonObject();
            if (json.has("payload") && json.get("payload").isJsonPrimitive()) {
                return json.get("payload").getAsString();
            }
            throw new IOException("Invalid server response: missing 'payload'");
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to parse share response", e);
        }
    }

    private ShareCodeClient() {}
}
