package com.propertysearcher.scraper.exception;

/**
 * Thrown when an automated scraper encounters an anti-bot security barrier,
 * such as Cloudflare Turnstile, Cloudflare WAF 403 Challenge, or human verification CAPTCHA.
 */
public class AntiBotChallengeException extends RuntimeException {

    private final int statusCode;
    private final String detectedChallenge;
    private final String rayId;

    public AntiBotChallengeException(String message, int statusCode, String detectedChallenge, String rayId) {
        super(message);
        this.statusCode = statusCode;
        this.detectedChallenge = detectedChallenge;
        this.rayId = rayId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getDetectedChallenge() {
        return detectedChallenge;
    }

    public String getRayId() {
        return rayId;
    }
}
