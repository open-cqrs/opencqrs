/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Event data structure retrieved from an event store, conforming to the <a
 * href="https://github.com/cloudevents/spec">Cloud Events Specification</a>.
 *
 * @param source identifies the originating source of publication
 * @param subject an absolute path identifying the subject that the event is related to
 * @param type uniquely identifies the event type, specifically for being able to interpret the contained data structure
 * @param data a generic map structure containing the event payload
 * @param specVersion cloud events specification version
 * @param id a unique event identifier with respect to the originating event store
 * @param time the publication time-stamp
 * @param dataContentType the data content-type, always {@code application/json}
 * @param hash the hash of this event
 * @param predecessorHash the hash of the preceding event in the event store
 * @see EventCandidate
 * @see EsdbClient#read(String, Set)
 * @see EsdbClient#read(String, Set, Consumer)
 * @see EsdbClient#observe(String, Set, Consumer)
 */
public record Event(
        @NotBlank String source,
        @NotBlank String subject,
        @NotBlank String type,
        @NotNull Map<String, ?> data,
        @NotBlank String specVersion,
        @NotBlank String id,
        @NotNull Instant time,
        @NotBlank String dataContentType,
        String hash,
        @NotBlank String predecessorHash,
        String timeFromServer)
        implements Marshaller.ResponseElement {

    /**
     * Verifies the cryptographic hash of this event. This method validates the integrity of the event by recomputing
     * the hash based on the event's metadata and data, and comparing it with the stored hash.
     *
     * <p>The hash verification algorithm:
     *
     * <ol>
     *   <li>Constructs a metadata string from event fields (specVersion, id, predecessorHash, time, source, subject,
     *       type, dataContentType)
     *   <li>Computes SHA-256 hash of metadata
     *   <li>Computes SHA-256 hash of JSON-serialized data
     *   <li>Computes final SHA-256 hash by hashing the concatenation of the two hex hashes
     *   <li>Compares the result with the stored hash
     * </ol>
     *
     * @throws ClientException.ValidationException if the computed hash does not match the stored hash
     */
    public void verifyHash() throws ClientException.ValidationException {
        String metadata = String.join(
                "|", specVersion, id, predecessorHash, timeFromServer, source, subject, type, dataContentType);

        String metadataHashHex = sha256Hex(metadata);

        String dataJson;
        try {
            dataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ClientException.ValidationException("Failed to serialize event data for hash verification", e);
        }
        String dataHashHex = sha256Hex(dataJson);

        String finalHashHex = sha256Hex(metadataHashHex + dataHashHex);

        if (!finalHashHex.equals(hash)) {
            throw new ClientException.ValidationException("Hash verification failed");
        }
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
