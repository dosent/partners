package com.simbirsoft.partners.partner.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.application.PartnerProfileContactsProtector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

@Component
public final class RegistrationDataProtector implements PartnerProfileContactsProtector {
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int NONCE_LENGTH = 12;
    private static final int AUTH_TAG_BITS = 128;
    private static final String PROFILE_CONTACTS_AAD = "partner-profile-contacts:v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;
    private final String encryptionKey;
    private final String hashKey;

    public RegistrationDataProtector(
            ObjectMapper objectMapper,
            @Value("${partners.registration.encryption-key:}") String encryptionKey,
            @Value("${partners.registration.hash-key:}") String hashKey) {
        this.objectMapper = objectMapper;
        this.encryptionKey = encryptionKey;
        this.hashKey = hashKey;
    }

    public ProtectedRegistrationData protect(PartnerRegistrationData registrationData) {
        byte[] aesKey = decodeKey(encryptionKey, 32, "REGISTRATION_ENCRYPTION_KEY");
        byte[] hmacKey = decodeMinimumKey(hashKey, 32, "REGISTRATION_HASH_KEY");
        String taxIdentifierHash = registrationData.taxIdentifier() == null
                ? null : hashTaxIdentifier(registrationData.taxIdentifier(), hmacKey);
        try {
            byte[] payload = objectMapper.writeValueAsBytes(registrationData);
            return new ProtectedRegistrationData(encrypt(payload, taxIdentifierHash, aesKey), taxIdentifierHash);
        } catch (IOException exception) {
            throw new IllegalStateException("Registration data could not be protected");
        } finally {
            Arrays.fill(aesKey, (byte) 0);
            Arrays.fill(hmacKey, (byte) 0);
        }
    }

    public PartnerRegistrationData reveal(ProtectedRegistrationData protectedData) {
        byte[] aesKey = decodeKey(encryptionKey, 32, "REGISTRATION_ENCRYPTION_KEY");
        try {
            byte[] clearPayload = decrypt(protectedData.encryptedPayload(), protectedData.taxIdentifierHash(), aesKey);
            return objectMapper.readValue(clearPayload, PartnerRegistrationData.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Protected registration data could not be read");
        } finally {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    @Override
    public byte[] protect(ProfileContacts contacts) {
        byte[] aesKey = decodeKey(encryptionKey, 32, "REGISTRATION_ENCRYPTION_KEY");
        try {
            return encryptWithContext(objectMapper.writeValueAsBytes(contacts), PROFILE_CONTACTS_AAD, aesKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Partner profile contacts could not be protected");
        } finally {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    @Override
    public ProfileContacts reveal(byte[] encryptedContacts) {
        byte[] aesKey = decodeKey(encryptionKey, 32, "REGISTRATION_ENCRYPTION_KEY");
        try {
            return objectMapper.readValue(decryptWithContext(encryptedContacts, PROFILE_CONTACTS_AAD, aesKey), ProfileContacts.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Protected partner profile contacts could not be read");
        } finally {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    private static String hashTaxIdentifier(String taxIdentifier, byte[] key) {
        String normalized = taxIdentifier.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("taxIdentifier must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Registration data protection is unavailable");
        }
    }

    private static byte[] encrypt(byte[] clearPayload, String taxIdentifierHash, byte[] key) {
        return encrypt(clearPayload, associatedData(taxIdentifierHash), key);
    }

    private static byte[] encryptWithContext(byte[] clearPayload, String context, byte[] key) {
        return encrypt(clearPayload, context.getBytes(StandardCharsets.UTF_8), key);
    }

    private static byte[] encrypt(byte[] clearPayload, byte[] aad, byte[] key) {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(AUTH_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(clearPayload);
            return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Registration data protection is unavailable");
        }
    }

    private static byte[] decrypt(byte[] encryptedPayload, String taxIdentifierHash, byte[] key) {
        return decrypt(encryptedPayload, associatedData(taxIdentifierHash), key);
    }

    private static byte[] decryptWithContext(byte[] encryptedPayload, String context, byte[] key) {
        return decrypt(encryptedPayload, context.getBytes(StandardCharsets.UTF_8), key);
    }

    private static byte[] decrypt(byte[] encryptedPayload, byte[] aad, byte[] key) {
        if (encryptedPayload.length <= NONCE_LENGTH) {
            throw new IllegalStateException("Protected registration data is invalid");
        }
        byte[] nonce = Arrays.copyOfRange(encryptedPayload, 0, NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedPayload, NONCE_LENGTH, encryptedPayload.length);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(AUTH_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Protected registration data could not be authenticated");
        }
    }

    private static byte[] associatedData(String taxIdentifierHash) {
        return taxIdentifierHash == null ? new byte[0] : taxIdentifierHash.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeKey(String base64Key, int expectedLength, String settingName) {
        byte[] key = decodeMinimumKey(base64Key, expectedLength, settingName);
        if (key.length != expectedLength) {
            Arrays.fill(key, (byte) 0);
            throw new IllegalStateException(settingName + " must decode to exactly " + expectedLength + " bytes");
        }
        return key;
    }

    private static byte[] decodeMinimumKey(String base64Key, int minimumLength, String settingName) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("Registration data protection is not configured: " + settingName);
        }
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            if (key.length < minimumLength) {
                Arrays.fill(key, (byte) 0);
                throw new IllegalStateException(settingName + " must decode to at least " + minimumLength + " bytes");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(settingName + " must be valid Base64");
        }
    }
}
