package com.simbirsoft.partners.document.application;

public interface ObjectStorage {
    void put(String objectKey, byte[] content, String contentType);
    byte[] get(String objectKey);
    void delete(String objectKey);
}
