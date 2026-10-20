package util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class RegistrationCode {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RegistrationCode() { }

    public static String generate() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format(java.util.Locale.ROOT, "%06d", value);
    }

    public static String hash(String code) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(code.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    public static boolean matches(String code, String expectedHash)
            throws NoSuchAlgorithmException {
        if (code == null || expectedHash == null) return false;
        byte[] actual = hash(code).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
