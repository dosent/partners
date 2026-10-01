package com.simbirsoft.partners.partner.infrastructure;

public record ProtectedRegistrationData(byte[] encryptedPayload, String taxIdentifierHash) {
    public ProtectedRegistrationData {
        encryptedPayload = encryptedPayload.clone();
    }

    @Override
    public byte[] encryptedPayload() {
        return encryptedPayload.clone();
    }
}
