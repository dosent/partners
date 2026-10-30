package com.simbirsoft.partners.shared.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe validation failure that associates an API error with a submitted field. */
public class FieldValidationException extends ResponseStatusException {
    private final String code;
    private final String field;

    public FieldValidationException(String code, String field, String message) {
        this(HttpStatus.BAD_REQUEST, code, field, message);
    }

    public FieldValidationException(HttpStatus status, String code, String field, String message) {
        super(status, message);
        this.code = code;
        this.field = field;
    }

    public String getCode() { return code; }

    public String getField() { return field; }
}
