package de.bannerfall.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * The world's VAPID key pair (RFC 8292), a P-256 key: browsers bind a push subscription to its
 * public half, the server signs every push with the private half. Both are written as unpadded
 * base64url, the public key as the 65-byte uncompressed point and the private key as the 32-byte
 * scalar (the format every Web Push library prints). Kept in the world's {@code .env}, not in the
 * database, so a stolen dump can't send notifications in the world's name.
 */
public record VapidKeys(ECPublicKey publicKey, ECPrivateKey privateKey) {

    static final ECParameterSpec P256 = p256();

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64URL_DECODER = Base64.getUrlDecoder();

    /** A new random key pair (the host console's {@code push keys}). */
    public static VapidKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            return new VapidKeys((ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No P-256 in this JVM", e);
        }
    }

    /**
     * Reads the two base64url values and checks that they belong together.
     *
     * @throws IllegalArgumentException for a value that isn't a P-256 key, or two halves of
     *     different pairs
     */
    public static VapidKeys parse(String publicKey, String privateKey) {
        try {
            VapidKeys keys =
                    new VapidKeys(
                            publicKeyOf(decode(publicKey.strip())),
                            privateKeyOf(decode(privateKey.strip())));
            keys.requireMatching();
            return keys;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a VAPID key pair: " + e.getMessage(), e);
        }
    }

    /** The public key as the browser's {@code applicationServerKey} wants it. */
    public String publicKeyText() {
        return BASE64URL.encodeToString(uncompressed(publicKey));
    }

    /** The private key's 32-byte scalar, base64url. */
    public String privateKeyText() {
        return BASE64URL.encodeToString(fixed(privateKey.getS(), 32));
    }

    /** The 65-byte uncompressed point 0x04 || X || Y. */
    static byte[] uncompressed(ECPublicKey key) {
        ECPoint w = key.getW();
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(fixed(w.getAffineX(), 32), 0, out, 1, 32);
        System.arraycopy(fixed(w.getAffineY(), 32), 0, out, 33, 32);
        return out;
    }

    /** A P-256 public key from its uncompressed point (a subscription's {@code p256dh} too). */
    static ECPublicKey publicKeyOf(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 0x04) {
            throw new IllegalArgumentException("expected a 65-byte uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        // KeyFactory refuses points that are not on the curve
        return (ECPublicKey)
                KeyFactory.getInstance("EC")
                        .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    static ECPrivateKey privateKeyOf(byte[] scalar) throws GeneralSecurityException {
        if (scalar.length != 32) {
            throw new IllegalArgumentException("expected a 32-byte private key");
        }
        return (ECPrivateKey)
                KeyFactory.getInstance("EC")
                        .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
    }

    static byte[] decode(String base64url) {
        // browsers and libraries differ on padding; the standard decoder takes both
        return BASE64URL_DECODER.decode(base64url.replace("=", ""));
    }

    static String encode(byte[] bytes) {
        return BASE64URL.encodeToString(bytes);
    }

    /** Never the private key, not even in a log line. */
    @Override
    public String toString() {
        return "VapidKeys[" + publicKeyText() + "]";
    }

    /** Signs and verifies a test message: two halves of different pairs fail here. */
    private void requireMatching() throws GeneralSecurityException {
        byte[] probe = "bannerfall".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(probe);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(probe);
        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException("the public key doesn't belong to the private key");
        }
    }

    /** An unsigned number as exactly {@code length} big-endian bytes. */
    static byte[] fixed(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        }
        byte[] out = new byte[length];
        if (bytes.length > length) {
            // a leading sign byte
            System.arraycopy(bytes, bytes.length - length, out, 0, length);
        } else {
            System.arraycopy(bytes, 0, out, length - bytes.length, bytes.length);
        }
        return out;
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
