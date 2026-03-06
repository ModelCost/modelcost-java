package ai.modelcost.sdk.pii;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local PII detection scanner using regex patterns.
 * Thread-safe: compiled patterns are immutable, and scan/redact methods use no mutable state.
 *
 * <p>For metadata-only mode, use {@link #fullScan(String, List)} which runs PII, PHI,
 * secrets, and financial detection entirely in-process.</p>
 */
public class PiiScanner {

    // PII patterns
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b");
    private static final Pattern CREDIT_CARD_GENERIC = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+?1[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b");

    // Secrets patterns
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-[a-zA-Z0-9]{20,}");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN (?:RSA |EC |DSA )?PRIVATE KEY-----");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");
    private static final Pattern GENERIC_SECRET_PATTERN = Pattern.compile(
            "(?i)(?:password|api_key|apikey|secret|token|bearer)\\s*[:=]\\s*[\"']?([A-Za-z0-9_\\-/.]{8,})[\"']?");

    // Financial patterns
    private static final Pattern IBAN_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");

    // PHI medical terms
    private static final Set<String> MEDICAL_TERMS = Set.of(
            "diabetes", "hiv", "aids", "cancer", "tumor", "disease", "medication",
            "diagnosis", "treatment", "surgery", "prescription", "patient", "doctor",
            "hospital", "clinic", "medical record", "insulin", "prozac", "chemotherapy",
            "depression", "anxiety", "bipolar", "schizophrenia", "hepatitis",
            "tuberculosis", "epilepsy", "asthma", "arthritis", "alzheimer"
    );

    private static final List<PatternInfo> PATTERNS;

    static {
        List<PatternInfo> patterns = new ArrayList<>();
        patterns.add(new PatternInfo(SSN_PATTERN, "ssn", "pii"));
        patterns.add(new PatternInfo(CREDIT_CARD_PATTERN, "credit_card", "pci"));
        patterns.add(new PatternInfo(EMAIL_PATTERN, "email", "pii"));
        patterns.add(new PatternInfo(PHONE_PATTERN, "phone", "pii"));
        PATTERNS = Collections.unmodifiableList(patterns);
    }

    private record PatternInfo(Pattern pattern, String type, String subtype) {
    }

    /**
     * Result of a PII scan operation.
     */
    public static class PiiResult {
        private final boolean detected;
        private final List<PiiEntity> entities;
        private final String redactedText;

        public PiiResult(boolean detected, List<PiiEntity> entities, String redactedText) {
            this.detected = detected;
            this.entities = Collections.unmodifiableList(entities);
            this.redactedText = redactedText;
        }

        public boolean isDetected() {
            return detected;
        }

        public List<PiiEntity> getEntities() {
            return entities;
        }

        public String getRedactedText() {
            return redactedText;
        }
    }

    /**
     * A single PII entity detected in text.
     */
    public static class PiiEntity {
        private final String type;
        private final String value;
        private final int start;
        private final int end;

        public PiiEntity(String type, String value, int start, int end) {
            this.type = type;
            this.value = value;
            this.start = start;
            this.end = end;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }

    /**
     * A governance violation detected by the full scanner.
     */
    public static class GovernanceViolation {
        private final String category;
        private final String type;
        private final String severity;
        private final int start;
        private final int end;

        public GovernanceViolation(String category, String type, String severity, int start, int end) {
            this.category = category;
            this.type = type;
            this.severity = severity;
            this.start = start;
            this.end = end;
        }

        public String getCategory() { return category; }
        public String getType() { return type; }
        public String getSeverity() { return severity; }
        public int getStart() { return start; }
        public int getEnd() { return end; }
    }

    /**
     * Result of a full governance scan across all categories.
     */
    public static class FullScanResult {
        private final boolean detected;
        private final List<GovernanceViolation> violations;
        private final List<String> categories;

        public FullScanResult(boolean detected, List<GovernanceViolation> violations, List<String> categories) {
            this.detected = detected;
            this.violations = Collections.unmodifiableList(violations);
            this.categories = Collections.unmodifiableList(categories);
        }

        public boolean isDetected() { return detected; }
        public List<GovernanceViolation> getViolations() { return violations; }
        public List<String> getCategories() { return categories; }
    }

    /**
     * Scans text for PII entities and returns the result with redacted text.
     */
    public PiiResult scan(String text) {
        if (text == null || text.isEmpty()) {
            return new PiiResult(false, Collections.emptyList(), text);
        }

        List<PiiEntity> entities = new ArrayList<>();

        for (PatternInfo patternInfo : PATTERNS) {
            Matcher matcher = patternInfo.pattern().matcher(text);
            while (matcher.find()) {
                entities.add(new PiiEntity(
                        patternInfo.type(),
                        matcher.group(),
                        matcher.start(),
                        matcher.end()
                ));
            }
        }

        entities.sort((a, b) -> Integer.compare(a.start, b.start));
        String redactedText = redact(text);
        return new PiiResult(!entities.isEmpty(), entities, redactedText);
    }

    /**
     * Full governance scan across multiple categories.
     * Used in metadata-only mode where content never leaves the customer's environment.
     */
    public FullScanResult fullScan(String text, List<String> categories) {
        if (text == null || text.isEmpty()) {
            return new FullScanResult(false, Collections.emptyList(), Collections.emptyList());
        }

        if (categories == null) {
            categories = List.of("pii", "phi", "secrets", "financial");
        }

        List<GovernanceViolation> violations = new ArrayList<>();
        Set<String> detectedCategories = new LinkedHashSet<>();

        if (categories.contains("pii")) {
            List<GovernanceViolation> pii = scanPiiViolations(text);
            if (!pii.isEmpty()) {
                violations.addAll(pii);
                detectedCategories.add("pii");
            }
        }

        if (categories.contains("phi")) {
            List<GovernanceViolation> phi = scanPhi(text);
            if (!phi.isEmpty()) {
                violations.addAll(phi);
                detectedCategories.add("phi");
            }
        }

        if (categories.contains("secrets")) {
            List<GovernanceViolation> secrets = scanSecrets(text);
            if (!secrets.isEmpty()) {
                violations.addAll(secrets);
                detectedCategories.add("secrets");
            }
        }

        if (categories.contains("financial")) {
            List<GovernanceViolation> financial = scanFinancial(text);
            if (!financial.isEmpty()) {
                violations.addAll(financial);
                detectedCategories.add("financial");
            }
        }

        return new FullScanResult(!violations.isEmpty(), violations, new ArrayList<>(detectedCategories));
    }

    /**
     * Redacts PII from text by replacing matches with asterisks.
     */
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        for (PatternInfo patternInfo : PATTERNS) {
            Matcher matcher = patternInfo.pattern().matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String replacement = "*".repeat(matcher.group().length());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    // ─── Category Scanners ────────────────────────────────────────────

    private List<GovernanceViolation> scanPiiViolations(String text) {
        List<GovernanceViolation> violations = new ArrayList<>();

        // SSN
        Matcher ssnMatcher = SSN_PATTERN.matcher(text);
        while (ssnMatcher.find()) {
            violations.add(new GovernanceViolation("pii", "ssn", "critical", ssnMatcher.start(), ssnMatcher.end()));
        }

        // Email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            violations.add(new GovernanceViolation("pii", "email", "high", emailMatcher.start(), emailMatcher.end()));
        }

        // Credit card (Luhn-validated)
        Matcher ccMatcher = CREDIT_CARD_GENERIC.matcher(text);
        while (ccMatcher.find()) {
            String digits = ccMatcher.group().replaceAll("[\\s-]", "");
            if (isValidLuhn(digits)) {
                violations.add(new GovernanceViolation("pii", "credit_card", "critical", ccMatcher.start(), ccMatcher.end()));
            }
        }

        // Phone
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        while (phoneMatcher.find()) {
            violations.add(new GovernanceViolation("pii", "phone", "medium", phoneMatcher.start(), phoneMatcher.end()));
        }

        return violations;
    }

    private List<GovernanceViolation> scanPhi(String text) {
        String textLower = text.toLowerCase();
        boolean hasMedicalContext = MEDICAL_TERMS.stream().anyMatch(textLower::contains);

        if (!hasMedicalContext) {
            return Collections.emptyList();
        }

        List<GovernanceViolation> piiViolations = scanPiiViolations(text);
        List<GovernanceViolation> phiViolations = new ArrayList<>();
        for (GovernanceViolation v : piiViolations) {
            phiViolations.add(new GovernanceViolation("phi", "phi_" + v.getType(), "critical", v.getStart(), v.getEnd()));
        }
        return phiViolations;
    }

    private List<GovernanceViolation> scanSecrets(String text) {
        List<GovernanceViolation> violations = new ArrayList<>();

        // OpenAI API keys
        Matcher m = OPENAI_KEY_PATTERN.matcher(text);
        while (m.find()) {
            violations.add(new GovernanceViolation("secrets", "api_key_openai", "critical", m.start(), m.end()));
        }

        // AWS Access Keys
        m = AWS_KEY_PATTERN.matcher(text);
        while (m.find()) {
            violations.add(new GovernanceViolation("secrets", "api_key_aws", "critical", m.start(), m.end()));
        }

        // Private keys
        m = PRIVATE_KEY_PATTERN.matcher(text);
        while (m.find()) {
            violations.add(new GovernanceViolation("secrets", "private_key", "critical", m.start(), m.end()));
        }

        // JWT tokens
        m = JWT_PATTERN.matcher(text);
        while (m.find()) {
            violations.add(new GovernanceViolation("secrets", "jwt_token", "high", m.start(), m.end()));
        }

        // Generic secrets
        m = GENERIC_SECRET_PATTERN.matcher(text);
        while (m.find()) {
            violations.add(new GovernanceViolation("secrets", "generic_secret", "critical", m.start(), m.end()));
        }

        return violations;
    }

    private List<GovernanceViolation> scanFinancial(String text) {
        List<GovernanceViolation> violations = new ArrayList<>();

        // Credit card (Luhn-validated)
        Matcher ccMatcher = CREDIT_CARD_GENERIC.matcher(text);
        while (ccMatcher.find()) {
            String digits = ccMatcher.group().replaceAll("[\\s-]", "");
            if (isValidLuhn(digits)) {
                violations.add(new GovernanceViolation("financial", "credit_card", "critical", ccMatcher.start(), ccMatcher.end()));
            }
        }

        // IBAN
        Matcher ibanMatcher = IBAN_PATTERN.matcher(text);
        while (ibanMatcher.find()) {
            violations.add(new GovernanceViolation("financial", "iban", "high", ibanMatcher.start(), ibanMatcher.end()));
        }

        return violations;
    }

    // ─── Utilities ────────────────────────────────────────────────────

    /**
     * Luhn algorithm for credit card validation.
     */
    static boolean isValidLuhn(String number) {
        if (number == null || number.length() < 13 || number.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            char c = number.charAt(i);
            if (!Character.isDigit(c)) return false;
            int n = c - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
