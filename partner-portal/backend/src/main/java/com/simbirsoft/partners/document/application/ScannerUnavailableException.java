package com.simbirsoft.partners.document.application;

public class ScannerUnavailableException extends RuntimeException {
    public ScannerUnavailableException() { super("Document safety check is unavailable"); }
    public ScannerUnavailableException(Throwable cause) { super("Document safety check is unavailable", cause); }
}
