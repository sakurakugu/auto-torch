package com.sakurakugu.autotorch.forge;

import java.util.List;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.sakurakugu.autotorch.config.ConfigDefinitions;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ForgeConfigsTest {
    @Test
    void clientAndServerSpecsRegisterEverySetting() {
        assertDefinitionsRegistered(ForgeConfigs.CLIENT, ConfigDefinitions.CLIENT);
        assertDefinitionsRegistered(ForgeConfigs.SERVER, ConfigDefinitions.SERVER);
    }

    @Test
    void decimalAndStringSettingsCanBeReadAndUpdated() {
        ForgeConfigs.Backend backend = new ForgeConfigs.Backend();
        backend.define(ConfigDefinitions.LIGHT_OVERLAY_NEAR_LINE_WIDTH);
        backend.define(ConfigDefinitions.LIGHT_OVERLAY_ALWAYS_RISK_COLOR);
        backend.define(ConfigDefinitions.NEARBY_AUTO_TORCH_ITEM);
        backend.finish();
        CommentedConfig config = CommentedConfig.inMemory();
        backend.spec().correct(config);
        backend.spec().setConfig(config);

        assertEquals(2.5, backend.getDouble("lightOverlay.nearLineWidth", -1));
        assertEquals("FF3030", backend.getString("lightOverlay.alwaysRiskColor", "missing"));
        assertEquals("minecraft:torch", backend.getString("nearbyAutoTorch.item", "missing"));
        backend.setDouble("lightOverlay.nearLineWidth", 4.0);
        backend.setString("lightOverlay.alwaysRiskColor", "123ABC");
        backend.setString("nearbyAutoTorch.item", "minecraft:lantern");
        assertEquals(4.0, backend.getDouble("lightOverlay.nearLineWidth", -1));
        assertEquals("123ABC", backend.getString("lightOverlay.alwaysRiskColor", "missing"));
        assertEquals("minecraft:lantern", backend.getString("nearbyAutoTorch.item", "missing"));
    }

    private static void assertDefinitionsRegistered(ForgeConfigs.Backend backend,
            List<ConfigDefinitions.Value> definitions) {
        for (ConfigDefinitions.Value definition : definitions) {
            ForgeConfigSpec.ValueSpec spec = backend.spec().getSpec().get(definition.key());
            assertNotNull(spec, definition.key());
            assertEquals(definition.defaultValueText(), spec.getDefault().toString(), definition.key());
        }
    }
}
