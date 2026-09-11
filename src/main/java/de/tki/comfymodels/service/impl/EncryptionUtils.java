package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

@Component
public class EncryptionUtils {
    private static final String GCM_PREFIX = "$GCM$v1$";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final byte[] LEGACY_SALT = "ComfyUI-Vault-Salt-2026".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypts plaintext using AES/GCM/NoPadding (AEAD) with a per-operation random salt and IV.
     * Output format: $GCM$v1$[Base64(salt + iv + ciphertextAndTag)]
     */
    public String encrypt(String strToEncrypt, String password) throws Exception {
        if (strToEncrypt == null) {
            throw new IllegalArgumentException("String to encrypt must not be null");
        }

        // 1. Generate unique random 16-byte salt and 12-byte IV for GCM
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        // 2. Derive key using PBKDF2 with unique salt
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        // 3. Encrypt with AES-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] encrypted = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));

        // 4. Combine: salt (16 bytes) + iv (12 bytes) + encrypted (ciphertext + tag)
        byte[] combined = new byte[salt.length + iv.length + encrypted.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(iv, 0, combined, salt.length, iv.length);
        System.arraycopy(encrypted, 0, combined, salt.length + iv.length, encrypted.length);

        return GCM_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts ciphertext. Supports both modern $GCM$v1$ authenticated ciphertext
     * and legacy AES/CBC/PKCS5Padding vaults transparently.
     */
    public String decrypt(String strToDecrypt, String password) throws Exception {
        if (strToDecrypt == null || strToDecrypt.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted data is empty");
        }
        String trimmed = strToDecrypt.trim();

        if (trimmed.startsWith(GCM_PREFIX)) {
            return decryptGcm(trimmed.substring(GCM_PREFIX.length()), password);
        }

        return decryptLegacyCbc(trimmed, password);
    }

    private String decryptGcm(String base64Payload, String password) throws Exception {
        byte[] combined = Base64.getDecoder().decode(base64Payload);
        int minLength = SALT_LENGTH + GCM_IV_LENGTH + (GCM_TAG_LENGTH / 8);
        if (combined.length < minLength) {
            throw new IllegalArgumentException("Encrypted GCM payload too short");
        }

        byte[] salt = new byte[SALT_LENGTH];
        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);

        int cipherTextOffset = SALT_LENGTH + GCM_IV_LENGTH;
        int cipherTextLength = combined.length - cipherTextOffset;
        byte[] cipherText = new byte[cipherTextLength];
        System.arraycopy(combined, cipherTextOffset, cipherText, 0, cipherTextLength);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        byte[] decrypted = cipher.doFinal(cipherText);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String decryptLegacyCbc(String base64Payload, String password) throws Exception {
        byte[] combined = Base64.getDecoder().decode(base64Payload);
        if (combined.length < 16) {
            throw new IllegalArgumentException("Encrypted legacy data too short");
        }

        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        IvParameterSpec ivspec = new IvParameterSpec(iv);

        byte[] encrypted = new byte[combined.length - 16];
        System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), LEGACY_SALT, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
