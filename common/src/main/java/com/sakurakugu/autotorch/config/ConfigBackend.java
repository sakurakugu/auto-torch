package com.sakurakugu.autotorch.config;

/** Loader-neutral access to typed configuration values. */
public interface ConfigBackend {
    boolean getBoolean(String key, boolean defaultValue);

    int getInt(String key, int defaultValue);

    double getDouble(String key, double defaultValue);

    String getString(String key, String defaultValue);

    void setBoolean(String key, boolean value);

    void setInt(String key, int value);

    void setDouble(String key, double value);

    void setString(String key, String value);

    void save();
}

