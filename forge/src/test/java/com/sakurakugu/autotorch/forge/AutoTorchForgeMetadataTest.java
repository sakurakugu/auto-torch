package com.sakurakugu.autotorch.forge;

import cpw.mods.fml.common.Mod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutoTorchForgeMetadataTest {
    @Test
    void declaresLegacyForgeContainerDependency() {
        Mod metadata = AutoTorchForge.class.getAnnotation(Mod.class);

        assertNotNull(metadata);
        assertEquals("[1.7.10]", metadata.acceptedMinecraftVersions());
        assertEquals("required-after:Forge@[10,)", metadata.dependencies());
    }
}
