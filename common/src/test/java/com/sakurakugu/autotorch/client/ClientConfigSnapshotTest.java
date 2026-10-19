package com.sakurakugu.autotorch.client;

import com.sakurakugu.autotorch.config.ConfigBackend;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.sakurakugu.autotorch.config.ConfigDefinitions.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientConfigSnapshotTest {
    @AfterEach
    void restoreDefaults() {
        ClientConfig.install(new CountingBackend());
    }

    @Test
    void repeatedReadsUseCacheAndReloadPublishesANewSnapshot() {
        CountingBackend backend = new CountingBackend();
        backend.setInt(NEARBY_AUTO_TORCH_SCAN_INTERVAL.key(), 20);
        ClientConfig.install(backend);
        ClientConfigSnapshot before = ClientConfig.snapshot();
        int reads = backend.reads;
        for (int i = 0; i < 10000; i++) {
            assertSame(before, ClientConfig.snapshot());
            assertEquals(20, ClientConfig.nearbyScanInterval());
            assertEquals(20, ClientConfig.get(NEARBY_AUTO_TORCH_SCAN_INTERVAL));
            assertEquals(0xFF3030, before.lightOverlayAlwaysRiskColorRgb);
            ClientConfig.doubleValue(LIGHT_OVERLAY_NEAR_LINE_WIDTH);
            ClientConfig.stringValue(SELECTION_TOOL_ITEM);
        }
        assertEquals(reads, backend.reads);
        backend.setInt(NEARBY_AUTO_TORCH_SCAN_INTERVAL.key(), 30);
        assertEquals(20, ClientConfig.nearbyScanInterval());
        ClientConfig.reload();
        assertEquals(30, ClientConfig.nearbyScanInterval());
        assertEquals(20, before.nearbyAutoTorchScanInterval);
        assertNotSame(before, ClientConfig.snapshot());
        assertEquals(0, backend.saves);
    }

    @Test
    void settersAndResetRefreshTheCache() {
        CountingBackend backend = new CountingBackend();
        ClientConfig.install(backend);
        ClientConfigSnapshot before = ClientConfig.snapshot();
        ClientConfig.setNearbyAutoTorchThreshold(8);
        assertEquals(8, ClientConfig.nearbyAutoTorchThreshold());
        ClientConfig.set(NEARBY_AUTO_TORCH_SCAN_INTERVAL, "25");
        assertEquals(25, ClientConfig.nearbyScanInterval());
        ClientConfig.resetNearbyAutoTorchDefaults();
        assertEquals(10, ClientConfig.nearbyScanInterval());
        assertEquals(4, ClientConfig.nearbyAutoTorchThreshold());
        assertEquals(4, before.nearbyAutoTorchLightThreshold);
        assertEquals(3, backend.saves);
    }

    @Test
    void resettingOneSettingPreservesItsSiblingsAndOtherSections() {
        CountingBackend backend = new CountingBackend();
        backend.setInt(NEARBY_AUTO_TORCH_SCAN_INTERVAL.key(), 30);
        backend.setInt(NEARBY_AUTO_TORCH_LIGHT_THRESHOLD.key(), 8);
        backend.setBoolean(LIGHT_OVERLAY_NUMBER_ROTATION.key(), false);
        ClientConfig.install(backend);

        ClientConfig.resetDefaults(NEARBY_AUTO_TORCH_SCAN_INTERVAL);

        assertEquals(10, ClientConfig.nearbyScanInterval());
        assertEquals(8, ClientConfig.nearbyAutoTorchThreshold());
        assertFalse(ClientConfig.rotatesLightOverlayNumbers());
        assertEquals(1, backend.saves);
    }

    @Test
    void resettingASectionDoesNotResetTheOtherSectionOfItsFeature() {
        CountingBackend backend = new CountingBackend();
        backend.setString(SELECTION_OVERLAY_MAIN_COLOR.key(), "123456");
        backend.setDouble(SELECTION_OVERLAY_NEAR_LINE_WIDTH.key(), 5.0);
        backend.setInt(TASK_PROGRESS_INTERVAL.key(), 40);
        ClientConfig.install(backend);

        ClientConfig.resetDefaults("selectionOverlay");

        assertEquals("50FF70", ClientConfig.stringValue(SELECTION_OVERLAY_MAIN_COLOR));
        assertEquals(3.0, ClientConfig.doubleValue(SELECTION_OVERLAY_NEAR_LINE_WIDTH));
        assertEquals(40, ClientConfig.intValue(TASK_PROGRESS_INTERVAL));
        assertEquals(1, backend.saves);
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.resetDefaults("selection"));
        assertEquals(1, backend.saves);
        assertEquals(40, ClientConfig.intValue(TASK_PROGRESS_INTERVAL));
    }

    @Test
    void resettingOneSettingsPagePreservesOtherGroups() {
        for (ClientCategory category : ClientCategory.values()) {
            CountingBackend backend = new CountingBackend();
            backend.setInt(NEARBY_AUTO_TORCH_SCAN_INTERVAL.key(), 30);
            backend.setBoolean(LIGHT_OVERLAY_NUMBER_ROTATION.key(), false);
            backend.setInt(TASK_PROGRESS_INTERVAL.key(), 40);
            backend.setString(SELECTION_OVERLAY_MAIN_COLOR.key(), "123456");
            ClientConfig.install(backend);

            ClientConfig.resetDefaults(category);

            assertEquals(category == ClientCategory.NEARBY_AUTO_TORCH ? 10 : 30, ClientConfig.nearbyScanInterval());
            assertEquals(category == ClientCategory.LIGHT_OVERLAY, ClientConfig.rotatesLightOverlayNumbers());
            assertEquals(category == ClientCategory.LIGHTING_TASK ? 10 : 40, ClientConfig.intValue(TASK_PROGRESS_INTERVAL));
            assertEquals(category == ClientCategory.LIGHTING_TASK ? "50FF70" : "123456",
                    ClientConfig.stringValue(SELECTION_OVERLAY_MAIN_COLOR));
            assertEquals(1, backend.saves);
        }
    }

    @Test
    void deferredInstallDoesNotReadUnloadedLoaderConfiguration() {
        CountingBackend backend = new CountingBackend();
        backend.setInt(NEARBY_AUTO_TORCH_SCAN_INTERVAL.key(), 40);
        ClientConfig.installDeferred(backend);
        assertEquals(0, backend.reads);
        ClientConfig.snapshot();
        assertEquals(0, backend.reads);
        ClientConfig.reload();
        assertEquals(40, ClientConfig.nearbyScanInterval());
    }

    @Test
    void colorsAndSphereTablesAreValidatedOnceAndOldTablesRemainUsable() {
        CountingBackend backend = new CountingBackend();
        backend.setString(LIGHT_OVERLAY_ALWAYS_RISK_COLOR.key(), "a1B2c3");
        backend.setString(SELECTION_OVERLAY_MAIN_COLOR.key(), "invalid");
        backend.setDouble(LIGHT_OVERLAY_NEAR_LINE_WIDTH.key(), Double.NaN);
        ClientConfigSnapshot before = ClientConfigSnapshot.load(backend);
        assertEquals(0xA1B2C3, before.lightOverlayAlwaysRiskColorRgb);
        assertEquals(0x50FF70, before.selectionOverlayMainColorRgb);
        assertEquals(2.5, before.lightOverlayNearLineWidth);
        backend.setInt(SELECTION_OVERLAY_SPHERE_LONGITUDE_SEGMENTS.key(), 48);
        backend.setInt(SELECTION_OVERLAY_SPHERE_LATITUDE_SEGMENTS.key(), 48);
        ClientConfigSnapshot after = ClientConfigSnapshot.load(backend);
        assertEquals(24, before.selectionOverlaySphereLongitudeSegments);
        assertEquals(48, after.selectionOverlaySphereLongitudeSegments);
        assertEquals(1.0, before.longitudeCos(24), 1.0e-12);
        assertEquals(-1.0, after.latitudeSin(0), 1.0e-12);
        for (int i = 0; i <= 48; i++) {
            assertEquals(Math.cos(Math.PI * 2 * i / 48), after.longitudeCos(i), 1.0e-12);
            assertEquals(Math.sin(Math.PI * 2 * i / 48), after.longitudeSin(i), 1.0e-12);
            assertEquals(Math.cos(-Math.PI / 2 + Math.PI * i / 48), after.latitudeCos(i), 1.0e-12);
            assertEquals(Math.sin(-Math.PI / 2 + Math.PI * i / 48), after.latitudeSin(i), 1.0e-12);
        }
    }

    @Test
    void legacyOffStepTorchLimitMigratesToNearestStep() {
        CountingBackend backend = new CountingBackend();
        backend.setInt(TASK_DEFAULT_MAX_TORCHES.key(), 100);
        ClientConfigSnapshot snapshot = ClientConfigSnapshot.load(backend);
        assertEquals(128, snapshot.taskDefaultMaxTorches);
    }

    private static final class CountingBackend implements ConfigBackend {
        private final Map<String, Object> values = new HashMap<>();
        private int reads;
        private int saves;

        public boolean getBoolean(String key, boolean fallback) { reads++; return (Boolean) values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { reads++; return (Integer) values.getOrDefault(key, fallback); }
        public double getDouble(String key, double fallback) { reads++; return (Double) values.getOrDefault(key, fallback); }
        public String getString(String key, String fallback) { reads++; return (String) values.getOrDefault(key, fallback); }
        public void setBoolean(String key, boolean value) { values.put(key, value); }
        public void setInt(String key, int value) { values.put(key, value); }
        public void setDouble(String key, double value) { values.put(key, value); }
        public void setString(String key, String value) { values.put(key, value); }
        public void save() { saves++; }
    }
}
