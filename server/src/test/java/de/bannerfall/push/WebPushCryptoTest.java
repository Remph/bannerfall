package de.bannerfall.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Web Push encryption (RFC 8291) and the VAPID header (RFC 8292). */
class WebPushCryptoTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The example of RFC 8291 Appendix A, byte for byte. */
    @Test
    void encryptsTheRfcExample() throws Exception {
        byte[] plaintext =
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        VapidKeys sender =
                VapidKeys.parse(
                        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
                        "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] p256dh =
                VapidKeys.decode(
                        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = VapidKeys.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = VapidKeys.decode("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] body =
                WebPushCrypto.encrypt(
                        plaintext, p256dh, auth, sender.publicKey(), sender.privateKey(), salt);

        assertThat(VapidKeys.encode(body))
                .isEqualTo(
                        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    /** A browser's side: decrypting with its own private key gives the message back. */
    @Test
    void theReceiverDecryptsAFreshMessage() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair browser = generator.generateKeyPair();
        byte[] p256dh = VapidKeys.uncompressed((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        byte[] message =
                "{\"kind\":\"ATTACK\",\"text\":\"Grüße\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(message, p256dh, auth);

        assertThat(decrypt(body, (ECPrivateKey) browser.getPrivate(), p256dh, auth))
                .isEqualTo(message);
        // a new salt and key every time
        assertThat(WebPushCrypto.encrypt(message, p256dh, auth)).isNotEqualTo(body);
    }

    @Test
    void refusesAMessageTooLongForOnePush() {
        byte[] p256dh = VapidKeys.uncompressed(VapidKeys.generate().publicKey());
        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[4000], p256dh, new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theVapidHeaderIsSignedForThePushServicesOrigin() throws Exception {
        VapidKeys keys = VapidKeys.generate();
        String header =
                WebPushCrypto.vapidAuthorization(
                        URI.create("https://fcm.googleapis.com/fcm/send/abc:def"),
                        keys,
                        "mailto:host@example.com",
                        Instant.parse("2026-10-19T12:00:00Z"));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + keys.publicKeyText());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        JsonNode head = JSON.readTree(VapidKeys.decode(parts[0]));
        assertThat(head.path("alg").asString()).isEqualTo("ES256");
        JsonNode claims = JSON.readTree(VapidKeys.decode(parts[1]));
        assertThat(claims.path("aud").asString()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.path("exp").asLong())
                .isEqualTo(Instant.parse("2026-10-20T00:00:00Z").getEpochSecond());
        assertThat(claims.path("sub").asString()).isEqualTo("mailto:host@example.com");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keys.publicKey());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(VapidKeys.decode(parts[2]))).isTrue();
    }

    @Test
    void keysRoundTripAndMismatchedHalvesAreRefused() {
        VapidKeys keys = VapidKeys.generate();
        VapidKeys read = VapidKeys.parse(keys.publicKeyText(), keys.privateKeyText());
        assertThat(read.publicKeyText()).isEqualTo(keys.publicKeyText());
        assertThat(read.privateKeyText()).isEqualTo(keys.privateKeyText());
        assertThat(read.toString()).doesNotContain(keys.privateKeyText());

        VapidKeys other = VapidKeys.generate();
        assertThatThrownBy(() -> VapidKeys.parse(keys.publicKeyText(), other.privateKeyText()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VapidKeys.parse("not-a-key", keys.privateKeyText()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** RFC 8291 from the receiving end, written independently of the sender's code. */
    private static byte[] decrypt(byte[] body, ECPrivateKey receiver, byte[] p256dh, byte[] auth)
            throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int idLength = body[20] & 0xff;
        byte[] senderPoint = Arrays.copyOfRange(body, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + idLength, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(receiver);
        agreement.doPhase(VapidKeys.publicKeyOf(senderPoint), true);
        byte[] secret = agreement.generateSecret();
        byte[] info = new byte[14 + 65 + 65];
        System.arraycopy("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), 0, info, 0, 14);
        System.arraycopy(p256dh, 0, info, 14, 65);
        System.arraycopy(senderPoint, 0, info, 79, 65);
        byte[] ikm = WebPushCrypto.hkdf(auth, secret, info, 32);
        byte[] cek =
                WebPushCrypto.hkdf(
                        salt,
                        ikm,
                        "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
                        16);
        byte[] nonce =
                WebPushCrypto.hkdf(
                        salt,
                        ikm,
                        "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
                        12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }
}
