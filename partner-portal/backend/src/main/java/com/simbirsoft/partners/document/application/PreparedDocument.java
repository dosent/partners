package com.simbirsoft.partners.document.application;

/** Validated, scanned content shared by document use cases. */
public record PreparedDocument(String fileName, String contentType, byte[] bytes, String sha256) {
    public PreparedDocument { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
