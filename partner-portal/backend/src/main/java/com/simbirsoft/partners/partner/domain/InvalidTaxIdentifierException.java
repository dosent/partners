package com.simbirsoft.partners.partner.domain;

/** A tax identifier does not match the supported jurisdiction's syntax or checksum. */
public class InvalidTaxIdentifierException extends IllegalArgumentException {
    public InvalidTaxIdentifierException(String message) {
        super(message);
    }
}
