package com.inventory;

import com.inventory.util.PasswordHasher;

import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class PasswordHasherTest {
    private PasswordHasherTest() {
    }

    public static void main(String[] args) throws GeneralSecurityException {
        char[] password = {'t', 'e', 's', 't', '-', 'p', 'a', 's', 's'};
        char[] incorrectPassword = {'w', 'r', 'o', 'n', 'g'};
        char[] emptyPassword = new char[0];
        char[] emptyPasswordCopy = new char[0];

        try {
            String firstHash = PasswordHasher.hash(password);
            require(firstHash != null && !firstHash.isBlank(), "Password hashing succeeds.");
            System.out.println("PASS: password hashing succeeds.");
            require(
                    firstHash.startsWith("pbkdf2-sha256$v1$"),
                    "Hash uses the versioned PBKDF2 format.");
            System.out.println("PASS: hash uses the versioned PBKDF2 format.");

            require(PasswordHasher.verify(password, firstHash), "Correct password verifies.");
            System.out.println("PASS: correct password verifies.");
            require(
                    !PasswordHasher.verify(incorrectPassword, firstHash),
                    "Incorrect password is rejected.");
            System.out.println("PASS: incorrect password is rejected.");

            String secondHash = PasswordHasher.hash(password);
            require(!firstHash.equals(secondHash), "Separate hashes use random salts.");
            System.out.println("PASS: repeated hashing produces different salted hashes.");

            require(!PasswordHasher.verify(password, ""), "Empty stored hash is rejected.");
            require(
                    !PasswordHasher.verify(password, "not-a-valid-hash"),
                    "Malformed stored hash is rejected.");
            require(
                    !PasswordHasher.verify(password, "pbkdf2-sha256$v1$not-numbers"),
                    "Malformed PBKDF2 parameters are rejected.");
            System.out.println("PASS: malformed stored hashes are rejected safely.");

            expectExact(
                    IllegalArgumentException.class,
                    () -> PasswordHasher.hash(null),
                    "null password is rejected by hash");
            require(!PasswordHasher.verify(null, firstHash), "Null password does not verify.");
            require(!PasswordHasher.verify(password, null), "Null stored hash does not verify.");
            System.out.println("PASS: null passwords and hashes are rejected during verification.");

            String emptyHash = PasswordHasher.hash(emptyPassword);
            require(
                    PasswordHasher.verify(emptyPasswordCopy, emptyHash),
                    "An empty password verifies against its hash.");
            System.out.println(
                    "PASS: empty passwords can be hashed by PasswordHasher (the service rejects them).");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(incorrectPassword, '\0');
            Arrays.fill(emptyPassword, '\0');
            Arrays.fill(emptyPasswordCopy, '\0');
        }

        System.out.println("PasswordHasher checks completed.");
    }

    private static void expectExact(
            Class<? extends RuntimeException> expectedType,
            SecurityOperation operation,
            String checkName) throws GeneralSecurityException {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            if (exception.getClass() != expectedType) {
                throw new AssertionError(
                        checkName + " threw " + exception.getClass().getName()
                                + " instead of " + expectedType.getName(),
                        exception);
            }
            System.out.println("PASS: " + checkName + ".");
            return;
        }

        throw new AssertionError(checkName + " did not throw " + expectedType.getName() + ".");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface SecurityOperation {
        void run() throws GeneralSecurityException;
    }
}
