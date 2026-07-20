package com.sakurakugu.autotorch.fabric;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.sakurakugu.autotorch.config.ConfigBackend;

final class PropertiesConfigBackend implements ConfigBackend {
    private final Path path;
    private final Properties values = new Properties();

    PropertiesConfigBackend(Path path) {
        this.path = path;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                values.load(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read " + path, exception);
            }
        }
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(values.getProperty(key, Boolean.toString(fallback)));
    }

    @Override
    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(values.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override public void setBoolean(String key, boolean value) { values.setProperty(key, Boolean.toString(value)); }
    @Override public void setInt(String key, int value) { values.setProperty(key, Integer.toString(value)); }

    @Override
    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                values.store(writer, "Auto Torch configuration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write " + path, exception);
        }
    }
}
