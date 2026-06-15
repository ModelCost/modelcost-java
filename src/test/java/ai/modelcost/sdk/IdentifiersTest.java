package ai.modelcost.sdk;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class IdentifiersTest {

    @Test
    void detectsDirectIdentifiers() {
        assertTrue(Identifiers.looksLikeDirectIdentifier("jane.doe@hospital.org"));
        assertTrue(Identifiers.looksLikeDirectIdentifier("123-45-6789"));
        assertTrue(Identifiers.looksLikeDirectIdentifier("(415) 555-0132"));
        assertTrue(Identifiers.looksLikeDirectIdentifier("4111 1111 1111 1111")); // Luhn-valid
    }

    @Test
    void passesOpaqueValues() {
        for (String v : new String[]{"cust_123", "u_9f8a7b", "tenant-42"}) {
            assertFalse(Identifiers.looksLikeDirectIdentifier(v));
        }
    }

    @Test
    void nullAndBlankAreNullSafe() {
        assertNull(Identifiers.opaqueRef(null, null, "id", true));
        assertNull(Identifiers.opaqueRef("", null, "id", true));
        assertNull(Identifiers.opaqueRef("   ", null, "id", true));
        assertNull(Identifiers.opaqueRef(null, "secret", "id", true)); // never hashed into a constant
    }

    @Test
    void opaqueValuePassesThrough() {
        assertEquals("cust_123", Identifiers.opaqueRef("cust_123", null, "customer_id", true));
    }

    @Test
    void rawIdentifierRejectedWithoutSecret() {
        assertThrows(Identifiers.IdentifierException.class,
                () -> Identifiers.opaqueRef("jane.doe@hospital.org", null, "customer_id", true));
        assertThrows(Identifiers.IdentifierException.class,
                () -> Identifiers.opaqueRef("123-45-6789", null, "user_id", true));
    }

    @Test
    void hmacPseudonymizesWithSecret() {
        String ref = Identifiers.opaqueRef("jane.doe@hospital.org", "tenant-secret", "customer_id", true);
        assertNotNull(ref);
        assertTrue(ref.startsWith("mc_"));
        // stable for the same (value, secret)
        assertEquals(ref, Identifiers.opaqueRef("jane.doe@hospital.org", "tenant-secret", "customer_id", true));
        // raw value not recoverable / present
        assertFalse(ref.contains("jane"));
        assertFalse(ref.contains("hospital"));
    }

    @Test
    void hmacIsKeyedNotBareHash() throws Exception {
        String value = "123-45-6789";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder bare = new StringBuilder();
        for (byte b : d) {
            bare.append(Character.forDigit((b >> 4) & 0xF, 16));
            bare.append(Character.forDigit(b & 0xF, 16));
        }
        String keyed = Identifiers.opaqueRef(value, "tenant-secret", "user_id", true);
        assertNotNull(keyed);
        assertFalse(keyed.contains(bare.toString()), "must not be a trivially-reversible bare SHA-256");
        // different secrets => different refs (one tenant cannot correlate another)
        assertNotEquals(Identifiers.opaqueRef(value, "a", "user_id", true),
                Identifiers.opaqueRef(value, "b", "user_id", true));
    }

    @Test
    void featureLabelNotPseudonymizedButValidated() {
        assertEquals("chatbot", Identifiers.opaqueRef("chatbot", null, "feature", false));
        assertThrows(Identifiers.IdentifierException.class,
                () -> Identifiers.opaqueRef("patient jane.doe@x.com", null, "feature", false));
    }
}
