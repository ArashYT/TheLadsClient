package com.thelads.ladsapi.session;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Checks an attest token (PROTOCOL.md section 6): the Lads relay's signature that Mojang proved this player's account to it, made
 * for the nonce this server put in its hello. It proves a Lads relay session of that account, not which program runs on the
 * player's PC.
 */
public final class AttestVerifier {
    /** The Lads relay's public keys, base64 of the raw 32 bytes. The private key stays on the relay host. */
    public static final List<String> RELAY_KEYS = List.of("D/yqvlm70kf+0LhIpvStP+U53ZW0bI8E/Qns12/gamI=");
    /** The relay's token lifetime, and how far this server's clock may be off it, in seconds. */
    public static final long MAX_LIFE = 120, SKEW = 60;
    private static final byte[] X509_ED25519 = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    public enum Result { OK, MALFORMED, EXPIRED, BAD_SIGNATURE, NO_KEY }

    private final List<PublicKey> keys = new ArrayList<>();

    public AttestVerifier(Collection<String> base64Keys) {
        for (String key : base64Keys) {
            try {
                byte[] raw = Base64.getDecoder().decode(key.trim());
                if (raw.length != 32) continue;
                byte[] der = new byte[X509_ED25519.length + raw.length];
                System.arraycopy(X509_ED25519, 0, der, 0, X509_ED25519.length);
                System.arraycopy(raw, 0, der, X509_ED25519.length, raw.length);
                keys.add(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der)));
            } catch (Exception ignored) { // a bad key in config.yml, or a Java without Ed25519 (below 15): that key is left out
            }
        }
    }

    /** False when no key could be loaded: nobody can be verified, so the hello asks for no proof. */
    public boolean ready() {
        return !keys.isEmpty();
    }

    /**
     * The token is {@code v1.<expiry, unix seconds>.<base64url signature>}. Uuid, nonce and server id are this server's own: they
     * are never read from the client, so a token made for another player, hello or server fails the signature.
     */
    public Result check(String token, UUID uuid, String nonce, String sid, long nowSeconds) {
        if (keys.isEmpty()) return Result.NO_KEY;
        if (token == null || token.length() > 128 || uuid == null) return Result.MALFORMED;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals("v1") || !parts[1].matches("[0-9]{1,12}")) return Result.MALFORMED;
        long exp = Long.parseLong(parts[1]);
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return Result.MALFORMED;
        }
        if (signature.length != 64) return Result.MALFORMED;
        byte[] message = ("lads-attest-v1|" + uuid.toString().replace("-", "") + "|" + nonce + "|" + sid + "|" + exp)
                .getBytes(StandardCharsets.UTF_8);
        if (!signed(message, signature)) return Result.BAD_SIGNATURE;
        return nowSeconds > exp + SKEW || exp > nowSeconds + MAX_LIFE + SKEW ? Result.EXPIRED : Result.OK;
    }

    private boolean signed(byte[] message, byte[] signature) {
        for (PublicKey key : keys) {
            try {
                Signature check = Signature.getInstance("Ed25519");
                check.initVerify(key);
                check.update(message);
                if (check.verify(signature)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
