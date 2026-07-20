package dev.sakurakugu.autotorch.config;

/** Loader-neutral access to typed configuration values. */
public interface ConfigBackend {
    boolean getBoolean(String key, boolean defaultValue);

    int getInt(String key, int defaultValue);

    void setBoolean(String key, boolean value);

    void setInt(String key, int value);

    void save();
}
