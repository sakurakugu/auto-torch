package com.sakurakugu.autotorch.forge;

import net.minecraftforge.fml.common.Mod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutoTorchForgeMetadataTest {
    @Test
    void declaresLegacyForgeContainerDependency() {
        Mod metadata = AutoTorchForge.class.getAnnotation(Mod.class);

        assertNotNull(metadata);
        assertEquals("[1.10.2]", metadata.acceptedMinecraftVersions());
        assertEquals("required-after:Forge@[12,)", metadata.dependencies());
    }
}
