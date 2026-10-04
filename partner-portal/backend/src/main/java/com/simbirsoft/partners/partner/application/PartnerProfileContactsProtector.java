package com.simbirsoft.partners.partner.application;

public interface PartnerProfileContactsProtector {
    byte[] protect(ProfileContacts contacts);
    ProfileContacts reveal(byte[] encryptedContacts);

    record ProfileContacts(String fullName, String email, String phone) { }
}
