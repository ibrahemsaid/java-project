package com.inventory.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_ALGORITHM = "pbkdf2-sha256";
    private static final String FORMAT_VERSION = "v1";
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int MAX_STORED_ITERATIONS = 2_000_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(char[] password) throws GeneralSecurityException {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null.");
        }

        char[] temporaryPassword = password.clone();
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] derivedKey = null;
        try {
            derivedKey = deriveKey(temporaryPassword, salt, ITERATIONS, KEY_LENGTH_BITS);
            return FORMAT_ALGORITHM + "$" + FORMAT_VERSION + "$" + ITERATIONS + "$"
                    + KEY_LENGTH_BITS + "$"
                    + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$"
                    + Base64.getEncoder().withoutPadding().encodeToString(derivedKey);
        } finally {
            Arrays.fill(temporaryPassword, '\0');
            Arrays.fill(salt, (byte) 0);
            if (derivedKey != null) {
                Arrays.fill(derivedKey, (byte) 0);
            }
        }
    }

    public static boolean verify(char[] password, String storedHash)
            throws GeneralSecurityException {
        if (password == null || storedHash == null) {
            return false;
        }

        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 6
                || !FORMAT_ALGORITHM.equals(parts[0])
                || !FORMAT_VERSION.equals(parts[1])) {
            return false;
        }

        final int iterations;
        final int keyLengthBits;
        final byte[] salt;
        final byte[] expectedKey;
        try {
            iterations = Integer.parseInt(parts[2]);
            keyLengthBits = Integer.parseInt(parts[3]);
            salt = Base64.getDecoder().decode(parts[4]);
            expectedKey = Base64.getDecoder().decode(parts[5]);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (iterations < 1
                || iterations > MAX_STORED_ITERATIONS
                || keyLengthBits != KEY_LENGTH_BITS
                || salt.length < SALT_LENGTH_BYTES
                || salt.length > 64
                || expectedKey.length != KEY_LENGTH_BITS / Byte.SIZE) {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(expectedKey, (byte) 0);
            return false;
        }

        char[] temporaryPassword = password.clone();
        try {
            byte[] actualKey = deriveKey(temporaryPassword, salt, iterations, keyLengthBits);
            try {
                return MessageDigest.isEqual(expectedKey, actualKey);
            } finally {
                Arrays.fill(actualKey, (byte) 0);
            }
        } finally {
            Arrays.fill(temporaryPassword, '\0');
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(expectedKey, (byte) 0);
        }
    }

    private static byte[] deriveKey(
            char[] password, byte[] salt, int iterations, int keyLengthBits)
            throws GeneralSecurityException {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
            return keyFactory.generateSecret(keySpec).getEncoded();
        } finally {
            keySpec.clearPassword();
        }
    }
}
