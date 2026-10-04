package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.ObjectStorage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Minimal S3 Signature V4 client for the configured MinIO-compatible endpoint. */
@Component
class S3ObjectStorage implements ObjectStorage {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private final URI endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean bucketReady;

    S3ObjectStorage(@Value("${partners.documents.s3.endpoint:http://object-storage:9000}") String endpoint,
            @Value("${partners.documents.s3.bucket:partner-leads}") String bucket,
            @Value("${partners.documents.s3.access-key:}") String accessKey,
            @Value("${partners.documents.s3.secret-key:}") String secretKey,
            @Value("${partners.documents.s3.region:us-east-1}") String region) {
        this.endpoint = URI.create(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length()-1) : endpoint);
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
    }

    @Override public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        send("PUT", objectUri(key), content, contentType, 200, 201, 204);
    }
    @Override public byte[] get(String key) {
        ensureBucket();
        try {
            var response = send("GET", objectUri(key), new byte[0], "", 200);
            return response.body();
        } catch (StorageFailure failure) { throw failure; }
    }
    @Override public void delete(String key) {
        ensureBucket();
        send("DELETE", objectUri(key), new byte[0], "", 200, 204);
    }

    private synchronized void ensureBucket() {
        if (bucketReady) return;
        URI uri = bucketUri();
        try {
            var head = send("HEAD", uri, new byte[0], "", 200, 204, 404);
            if (head.statusCode() == 404) send("PUT", uri, new byte[0], "", 200, 201, 204, 409);
            bucketReady = true;
        } catch (RuntimeException failure) { throw new StorageFailure("S3 bucket is unavailable", failure); }
    }

    private HttpResponse<byte[]> send(String method, URI uri, byte[] body, String contentType, int... accepted) {
        try {
            String date = AMZ_DATE.format(Instant.now());
            String day = DAY.format(Instant.now());
            String payloadHash = sha256(body);
            String host = uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
            String canonicalHeaders = "host:" + host + "\n" + (contentType.isEmpty() ? "" : "content-type:" + contentType + "\n")
                    + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + date + "\n";
            String signedHeaders = contentType.isEmpty() ? "host;x-amz-content-sha256;x-amz-date"
                    : "content-type;host;x-amz-content-sha256;x-amz-date";
            String path = uri.getRawPath();
            String canonical = method + "\n" + path + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
            String scope = day + "/" + region + "/s3/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n" + sha256(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = hmac(hmac(hmac(hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), day), region), "s3"), "aws4_request");
            String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("x-amz-date", date).header("x-amz-content-sha256", payloadHash)
                    .header("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
            if (!contentType.isEmpty()) builder.header("Content-Type", contentType);
            builder.method(method, body.length == 0 && (method.equals("GET") || method.equals("HEAD") || method.equals("DELETE"))
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            for (int status : accepted) if (response.statusCode() == status) return response;
            throw new StorageFailure("S3 request failed with HTTP " + response.statusCode());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new StorageFailure("S3 request interrupted", interrupted);
        } catch (IOException failure) { throw new StorageFailure("S3 request failed", failure); }
    }

    private URI bucketUri() { return URI.create(endpoint + "/" + encode(bucket)); }
    private URI objectUri(String key) { return URI.create(endpoint + "/" + encode(bucket) + "/" + java.util.Arrays.stream(key.split("/", -1)).map(S3ObjectStorage::encode).reduce((a,b)->a+"/"+b).orElse("")); }
    private static String encode(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~"); }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static byte[] hmac(byte[] key, String value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static final class StorageFailure extends RuntimeException {
        StorageFailure(String message) { super(message); }
        StorageFailure(String message, Throwable cause) { super(message, cause); }
    }
}
