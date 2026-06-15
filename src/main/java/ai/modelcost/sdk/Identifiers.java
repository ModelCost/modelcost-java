package ai.modelcost.sdk;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Client-side identifier hardening.
 *
 * <p>Any value that references an entity ({@code customerId}, {@code userId},
 * {@code feature}) must leave the customer environment as an OPAQUE token — never a
 * raw direct/quasi identifier (email, SSN, phone, credit-card, name, MRN).
 *
 * <p>Two guarantees, enforced at the SDK boundary:
 * <ol>
 *   <li><b>Reject raw identifiers.</b> A value that looks like a direct identifier is
 *       refused unless it can be turned into a stable opaque key (see #2).</li>
 *   <li><b>Pseudonymize with a customer-held secret, never a bare hash.</b> When a
 *       stable key is needed and a secret is configured, the value becomes
 *       {@code HMAC-SHA256(secret, value)}. The secret never leaves the customer
 *       environment, so ModelCost cannot reverse the ref. A bare {@code SHA-256} is
 *       intentionally NOT used: for low-entropy identifiers it is trivially
 *       reversible and remains PHI under HIPAA Safe Harbor.</li>
 * </ol>
 */
public final class Identifiers {

    private static final Pattern EMAIL =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern PHONE =
            Pattern.compile("\\b(?:\\+?1[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b");
    private static final Pattern CREDIT_CARD =
            Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");

    private Identifiers() {
    }

    /** Thrown when a raw direct identifier is supplied with no way to pseudonymize it. */
    public static class IdentifierException extends RuntimeException {
        public IdentifierException(String message) {
            super(message);
        }
    }

    /** Returns true if {@code value} contains a recognisable direct identifier (PII). */
    public static boolean looksLikeDirectIdentifier(String value) {
        if (value == null) {
            return false;
        }
        if (EMAIL.matcher(value).find() || SSN.matcher(value).find() || PHONE.matcher(value).find()) {
            return true;
        }
        Matcher cc = CREDIT_CARD.matcher(value);
        while (cc.find()) {
            if (isValidLuhn(cc.group().replaceAll("[\\s-]", ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an opaque, transmit-safe form of {@code value} (or {@code null}).
     *
     * @param value        the raw value (may be null/blank -> returns null)
     * @param secret       customer-held HMAC secret (never transmitted); may be null
     * @param field        field name used in error messages
     * @param pseudonymize when true, HMAC the value if a secret is present; when false
     *                     (e.g. for human-readable {@code feature} labels) only validate
     * @return the opaque ref, or null
     * @throws IdentifierException if the value looks like a raw direct identifier and
     *                             cannot be pseudonymized
     */
    public static String opaqueRef(String value, String secret, String field, boolean pseudonymize) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (pseudonymize && secret != null && !secret.isBlank()) {
            return "mc_" + hmacSha256Hex(secret, text).substring(0, 32);
        }
        if (looksLikeDirectIdentifier(text)) {
            throw new IdentifierException(
                    "'" + field + "' value looks like a direct identifier (PII/PHI) and would leak. "
                            + "Pass an opaque token you control, or set MODELCOST_IDENTIFIER_SECRET "
                            + "to pseudonymize it locally (the secret never leaves your environment). "
                            + "ModelCost never receives raw identifiers.");
        }
        return text;
    }

    private static String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean isValidLuhn(String number) {
        if (number == null || number.length() < 13 || number.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            char c = number.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            int n = c - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
