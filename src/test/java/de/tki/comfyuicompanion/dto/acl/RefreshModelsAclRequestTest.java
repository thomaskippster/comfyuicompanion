package de.tki.comfyuicompanion.dto.acl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating serialization and factory methods of {@link RefreshModelsAclRequest}.
 */
public class RefreshModelsAclRequestTest {

    @Test
    void testSerializationForceReloadTrue() {
        RefreshModelsAclRequest req = RefreshModelsAclRequest.FORCE;
        assertTrue(req.forceReload());
        assertEquals("{\"force_reload\":true}", req.toJson());
    }

    @Test
    void testSerializationForceReloadFalse() {
        RefreshModelsAclRequest req = RefreshModelsAclRequest.NORMAL;
        assertFalse(req.forceReload());
        assertEquals("{\"force_reload\":false}", req.toJson());
    }

    @Test
    void testFactoryOf() {
        assertSame(RefreshModelsAclRequest.FORCE, RefreshModelsAclRequest.of(true));
        assertSame(RefreshModelsAclRequest.NORMAL, RefreshModelsAclRequest.of(false));
    }
}
