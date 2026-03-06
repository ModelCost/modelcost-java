package ai.modelcost.sdk.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiScannerTest {

    private PiiScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new PiiScanner();
    }

    @Test
    void detectsSsn() {
        PiiScanner.PiiResult result = scanner.scan("My SSN is 123-45-6789");

        assertTrue(result.isDetected());
        assertFalse(result.getEntities().isEmpty());

        PiiScanner.PiiEntity ssnEntity = result.getEntities().stream()
                .filter(e -> e.getType().equals("ssn"))
                .findFirst()
                .orElseThrow();

        assertEquals("123-45-6789", ssnEntity.getValue());
        assertEquals("ssn", ssnEntity.getType());
        assertTrue(ssnEntity.getStart() >= 0);
        assertTrue(ssnEntity.getEnd() > ssnEntity.getStart());
    }

    @Test
    void detectsCreditCard() {
        PiiScanner.PiiResult result = scanner.scan("Card: 4111111111111111");

        assertTrue(result.isDetected());

        PiiScanner.PiiEntity ccEntity = result.getEntities().stream()
                .filter(e -> e.getType().equals("credit_card"))
                .findFirst()
                .orElseThrow();

        assertEquals("4111111111111111", ccEntity.getValue());
    }

    @Test
    void detectsEmail() {
        PiiScanner.PiiResult result = scanner.scan("Contact me at test@example.com please");

        assertTrue(result.isDetected());

        PiiScanner.PiiEntity emailEntity = result.getEntities().stream()
                .filter(e -> e.getType().equals("email"))
                .findFirst()
                .orElseThrow();

        assertEquals("test@example.com", emailEntity.getValue());
    }

    @Test
    void detectsPhone() {
        PiiScanner.PiiResult result = scanner.scan("Call me at 555-123-4567");

        assertTrue(result.isDetected());

        PiiScanner.PiiEntity phoneEntity = result.getEntities().stream()
                .filter(e -> e.getType().equals("phone"))
                .findFirst()
                .orElseThrow();

        assertEquals("555-123-4567", phoneEntity.getValue());
    }

    @Test
    void cleanTextReturnsNoDetections() {
        PiiScanner.PiiResult result = scanner.scan("This is a perfectly clean text with no PII.");

        assertFalse(result.isDetected());
        assertTrue(result.getEntities().isEmpty());
        assertEquals("This is a perfectly clean text with no PII.", result.getRedactedText());
    }

    @Test
    void redactionReplacesWithAsterisks() {
        String text = "SSN: 123-45-6789, Email: test@example.com";
        String redacted = scanner.redact(text);

        assertFalse(redacted.contains("123-45-6789"));
        assertFalse(redacted.contains("test@example.com"));
        assertTrue(redacted.contains("***")); // Asterisks present
    }

    @Test
    void scanReturnsRedactedText() {
        PiiScanner.PiiResult result = scanner.scan("My SSN is 123-45-6789");

        assertTrue(result.isDetected());
        assertNotNull(result.getRedactedText());
        assertFalse(result.getRedactedText().contains("123-45-6789"));
    }

    @Test
    void nullTextReturnsNoDetections() {
        PiiScanner.PiiResult result = scanner.scan(null);
        assertFalse(result.isDetected());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    void emptyTextReturnsNoDetections() {
        PiiScanner.PiiResult result = scanner.scan("");
        assertFalse(result.isDetected());
        assertTrue(result.getEntities().isEmpty());
    }

    // ─── fullScan tests ─────────────────────────────────────────────

    @Test
    void fullScanDetectsOpenAiApiKey() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "My key is sk-abc123def456ghi789jkl012mno", null);

        assertTrue(result.isDetected());
        assertTrue(result.getCategories().contains("secrets"));

        PiiScanner.GovernanceViolation secret = result.getViolations().stream()
                .filter(v -> v.getType().equals("api_key_openai"))
                .findFirst()
                .orElseThrow();
        assertEquals("critical", secret.getSeverity());
    }

    @Test
    void fullScanDetectsAwsAccessKey() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "AWS key: AKIAIOSFODNN7EXAMPLE", null);

        assertTrue(result.isDetected());
        assertTrue(result.getCategories().contains("secrets"));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getType().equals("api_key_aws")));
    }

    @Test
    void fullScanDetectsPrivateKey() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "-----BEGIN RSA PRIVATE KEY-----\ncontent\n-----END RSA PRIVATE KEY-----", null);

        assertTrue(result.isDetected());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getType().equals("private_key")));
    }

    @Test
    void fullScanDetectsJwtToken() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U",
                null);

        assertTrue(result.isDetected());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getType().equals("jwt_token")));
    }

    @Test
    void fullScanDetectsGenericSecret() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "config: password=SuperSecretValue123", null);

        assertTrue(result.isDetected());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getType().equals("generic_secret")));
    }

    @Test
    void fullScanDetectsPhiWithMedicalContextAndPii() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "Patient with SSN 123-45-6789 has diabetes and needs insulin", null);

        assertTrue(result.isDetected());
        assertTrue(result.getCategories().contains("phi"));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getCategory().equals("phi")));
    }

    @Test
    void fullScanNoPhiWithoutPiiCooccurrence() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "The patient is being treated for diabetes",
                java.util.List.of("phi"));

        long phiCount = result.getViolations().stream()
                .filter(v -> v.getCategory().equals("phi"))
                .count();
        assertEquals(0, phiCount);
    }

    @Test
    void fullScanDetectsCreditCardWithLuhn() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "Card: 4111111111111111",
                java.util.List.of("financial"));

        assertTrue(result.isDetected());
        assertTrue(result.getCategories().contains("financial"));
    }

    @Test
    void fullScanDetectsIban() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "Transfer to DE89370400440532013000",
                java.util.List.of("financial"));

        assertTrue(result.isDetected());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getType().equals("iban")));
    }

    @Test
    void luhnValidationCorrectNumbers() {
        assertTrue(PiiScanner.isValidLuhn("4111111111111111"));
        assertTrue(PiiScanner.isValidLuhn("5500000000000004"));
    }

    @Test
    void luhnValidationIncorrectNumbers() {
        assertFalse(PiiScanner.isValidLuhn("1234567890123456"));
    }

    @Test
    void luhnValidationTooShort() {
        assertFalse(PiiScanner.isValidLuhn("123"));
        assertFalse(PiiScanner.isValidLuhn(""));
        assertFalse(PiiScanner.isValidLuhn(null));
    }

    @Test
    void fullScanCleanTextReturnsNoViolations() {
        PiiScanner.FullScanResult result = scanner.fullScan(
                "Just a normal business message.", null);

        assertFalse(result.isDetected());
        assertTrue(result.getViolations().isEmpty());
        assertTrue(result.getCategories().isEmpty());
    }

    @Test
    void fullScanEmptyTextReturnsNoViolations() {
        PiiScanner.FullScanResult result = scanner.fullScan("", null);
        assertFalse(result.isDetected());
    }

    @Test
    void fullScanCategoryFiltering() {
        String text = "SSN: 123-45-6789 and key: sk-abc123def456ghi789jkl012mno";

        PiiScanner.FullScanResult piiOnly = scanner.fullScan(text, java.util.List.of("pii"));
        assertTrue(piiOnly.getCategories().contains("pii"));
        assertFalse(piiOnly.getCategories().contains("secrets"));

        PiiScanner.FullScanResult secretsOnly = scanner.fullScan(text, java.util.List.of("secrets"));
        assertTrue(secretsOnly.getCategories().contains("secrets"));
        assertFalse(secretsOnly.getCategories().contains("pii"));
    }
}
