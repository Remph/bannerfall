package de.bannerfall.push;

import de.bannerfall.FakePushService;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A browser's side of a push subscription for the tests: its own key pair and auth secret, an
 * address at the {@link FakePushService}, and the decryption of what arrives there (RFC 8291, from
 * the receiving end).
 */
final class PushDevice {

    final String endpoint;
    final String p256dh;
    final String auth;
    private final ECPrivateKey privateKey;
    private final byte[] authBytes;

    private PushDevice(String endpoint) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        this.endpoint = endpoint;
        this.privateKey = (ECPrivateKey) pair.getPrivate();
        this.p256dh = VapidKeys.encode(VapidKeys.uncompressed((ECPublicKey) pair.getPublic()));
        this.authBytes = new byte[16];
        new SecureRandom().nextBytes(authBytes);
        this.auth = VapidKeys.encode(authBytes);
    }

    static PushDevice create() throws Exception {
        return new PushDevice(FakePushService.get().newEndpoint());
    }

    static PushDevice at(String endpoint) throws Exception {
        return new PushDevice(endpoint);
    }

    /** The body of {@code PUT /api/push/subscription}. */
    String subscription(String language) {
        return """
        {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"},"language":"%s"}
        """
                .formatted(endpoint, p256dh, auth, language);
    }

    /** The JSON text of a push that arrived for this device. */
    String decrypt(byte[] body) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int idLength = body[20] & 0xff;
        byte[] senderPoint = Arrays.copyOfRange(body, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + idLength, body.length);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(VapidKeys.publicKeyOf(senderPoint), true);
        byte[] secret = agreement.generateSecret();
        byte[] receiverPoint = VapidKeys.decode(p256dh);
        byte[] info = new byte[14 + 65 + 65];
        System.arraycopy("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), 0, info, 0, 14);
        System.arraycopy(receiverPoint, 0, info, 14, 65);
        System.arraycopy(senderPoint, 0, info, 79, 65);
        byte[] ikm = WebPushCrypto.hkdf(authBytes, secret, info, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, ascii("Content-Encoding: aes128gcm\0"), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, ascii("Content-Encoding: nonce\0"), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        if (padded[padded.length - 1] != 2) {
            throw new AssertionError("Not the last record");
        }
        return new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
