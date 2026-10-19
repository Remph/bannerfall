package de.bannerfall.push;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two pieces of cryptography Web Push needs, with the JDK's own algorithms (no Bouncy Castle,
 * no Web Push library): the message encryption for one subscription (RFC 8291, content coding
 * {@code aes128gcm} of RFC 8188), and the VAPID header that tells the push service who sends (RFC
 * 8292). The push service only ever sees the encrypted body.
 */
final class WebPushCrypto {

    /** One record holds the whole message (push services take at most 4096 bytes anyway). */
    static final int RECORD_SIZE = 4096;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WebPushCrypto() {}

    /**
     * The encrypted body for the subscription with the browser's public key {@code p256dh} (65
     * bytes) and its {@code auth} secret (16 bytes), with a fresh key pair and salt.
     */
    static byte[] encrypt(byte[] plaintext, byte[] p256dh, byte[] auth) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair ephemeral = generator.generateKeyPair();
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(
                    plaintext,
                    p256dh,
                    auth,
                    (ECPublicKey) ephemeral.getPublic(),
                    (ECPrivateKey) ephemeral.getPrivate(),
                    salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push encryption failed", e);
        }
    }

    /**
     * {@link #encrypt(byte[], byte[], byte[])} with a given key pair and salt (the RFC's example).
     */
    static byte[] encrypt(
            byte[] plaintext,
            byte[] p256dh,
            byte[] auth,
            ECPublicKey senderPublic,
            ECPrivateKey senderPrivate,
            byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length + 1 + 16 > RECORD_SIZE - 86) {
            throw new IllegalArgumentException("Push message too long: " + plaintext.length);
        }
        ECPublicKey receiver = VapidKeys.publicKeyOf(p256dh);
        byte[] senderPoint = VapidKeys.uncompressed(senderPublic);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(senderPrivate);
        agreement.doPhase(receiver, true);
        byte[] ecdhSecret = agreement.generateSecret();

        // RFC 8291 §3.4: combine the shared secret with the auth secret, then RFC 8188's key and
        // nonce from the salt
        byte[] keyInfo = concat(ascii("WebPush: info\0"), p256dh, senderPoint);
        byte[] ikm = hkdf(auth, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, ascii("Content-Encoding: aes128gcm\0"), 16);
        byte[] nonce = hkdf(salt, ikm, ascii("Content-Encoding: nonce\0"), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        // the delimiter 0x02 marks the last (and only) record
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + senderPoint.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) senderPoint.length).put(senderPoint);
        return concat(header.array(), ciphertext);
    }

    /**
     * The {@code Authorization} header for a push to {@code endpoint}: a JWT signed with the
     * world's key for the push service's origin, valid for 12 hours, and the public key.
     *
     * @param subject how the push service can reach the sender ({@code mailto:} or https); null
     *     leaves the claim out
     */
    static String vapidAuthorization(URI endpoint, VapidKeys keys, String subject, Instant now) {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        long expires = now.getEpochSecond() + 12 * 3600;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", audience);
        claims.put("exp", expires);
        if (subject != null) {
            claims.put("sub", subject);
        }
        String signingInput =
                VapidKeys.encode(ascii("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"))
                        + "."
                        + VapidKeys.encode(JSON.writeValueAsBytes(claims));
        try {
            // ES256 wants r || s (64 bytes), not DER
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(keys.privateKey());
            signer.update(ascii(signingInput));
            String jwt = signingInput + "." + VapidKeys.encode(signer.sign());
            return "vapid t=" + jwt + ", k=" + keys.publicKeyText();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID signature failed", e);
        }
    }

    /** HKDF-SHA-256 (RFC 5869) for at most one block of output. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length)
            throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
