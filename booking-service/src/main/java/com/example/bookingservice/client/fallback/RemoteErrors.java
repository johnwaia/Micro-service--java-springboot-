package com.example.bookingservice.client.fallback;

import com.example.bookingservice.exception.RemoteServiceException;
import com.example.bookingservice.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

/**
 * Traduit la cause recue par un fallback de circuit breaker :
 * <ul>
 *     <li>reponse 4xx du service distant = erreur metier, propagee telle quelle (statut + message) ;</li>
 *     <li>tout le reste (connexion refusee, timeout, 5xx, circuit ouvert) = service indisponible (503).</li>
 * </ul>
 */
public final class RemoteErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RemoteErrors() {
    }

    public static RuntimeException translate(String serviceName, Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof FeignException feign && feign.status() >= 400 && feign.status() < 500) {
                return new RemoteServiceException(serviceName, feign.status(), extractMessage(feign));
            }
        }
        String reason = cause == null ? "cause inconnue" : cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? " - " + cause.getMessage() : "");
        return new ServiceUnavailableException(serviceName + " est indisponible (" + reason + ")", cause);
    }

    private static String extractMessage(FeignException feign) {
        try {
            JsonNode body = MAPPER.readTree(feign.contentUTF8());
            if (body != null && body.hasNonNull("message")) {
                return body.get("message").asText();
            }
        } catch (Exception ignored) {
            // corps absent ou non JSON : on garde le message Feign
        }
        return feign.getMessage();
    }
}
