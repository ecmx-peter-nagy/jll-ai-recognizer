package com.jll.ai.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class AppConfig {

    private static final Logger log = LogManager.getLogger("com.jll.doxis.agent.AppConfig");
    private static final String CONFIG_FILE = "app.properties";
    private static final Properties properties = new Properties();

    static {
        // 1️⃣ Load defaults from inside the JAR (classpath)
        loadFromClasspath(CONFIG_FILE);

        // 2️⃣ Optionally merge external overrides (next to JAR)
        mergeExternalOverrides(CONFIG_FILE);
    }

    private AppConfig() {}

    // =========================================================
    // Load from inside JAR
    // =========================================================
    private static void loadFromClasspath(String fileName) {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                log.error("[AppConfig] Internal configuration '{}' not found in classpath (inside JAR).", fileName);
                throw new RuntimeException("Internal configuration '" + fileName + "' not found in classpath.");
            }
            properties.load(input);
            log.info("[AppConfig] Loaded internal defaults from JAR resource '{}'.", fileName);
        } catch (IOException e) {
            log.error("[AppConfig] Failed to load defaults from '{}': {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to load defaults from " + fileName, e);
        }
    }

    // =========================================================
    // Load external overrides (next to JAR)
    // =========================================================
    private static void mergeExternalOverrides(String fileName) {
        Path jarDir = getJarDirectory();
        if (jarDir == null) {
            log.debug("[AppConfig] Cannot resolve JAR directory; skipping external overrides.");
            return;
        }

        Path externalFile = jarDir.resolve(fileName);
        if (!Files.exists(externalFile)) {
            log.debug("[AppConfig] No external '{}' found in '{}'. Using internal defaults.", fileName, jarDir);
            return;
        }

        try (InputStream input = Files.newInputStream(externalFile)) {
            Properties overrides = new Properties();
            overrides.load(input);
            overrides.forEach((k, v) -> properties.setProperty(k.toString(), v.toString()));
            log.info("[AppConfig] External overrides applied from '{}'.", externalFile);
        } catch (Exception e) {
            log.error("[AppConfig] Failed to load external overrides '{}': {}", externalFile, e.getMessage());
        }
    }

    private static Path getJarDirectory() {
        try {
            String path = AppConfig.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            
            if (path.matches("^/[A-Za-z]:/.*")) {
                path = path.substring(1);
            }

            Path jarPath = Paths.get(path);
            return Files.isDirectory(jarPath) ? jarPath : jarPath.getParent();
        } catch (Exception e) {
            log.warn("[AppConfig] Could not determine JAR directory: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================
    // Getters
    // =========================================================
    public static String get(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            log.warn("[AppConfig] Configuration key '{}' not found.", key);
            throw new NullPointerException("Configuration key '" + key + "' not found.");
        }
        return value.trim();
    }

    public static String get(String key, String defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) {
            log.debug("[AppConfig] Key '{}' not found. Using default '{}'", key, defaultValue);
            return defaultValue;
        }
        return val.trim();
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public static long getLong(String key) {
        return Long.parseLong(get(key));
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (Exception e) {
            log.warn("[AppConfig] Invalid integer for key '{}', using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (Exception e) {
            log.warn("[AppConfig] Invalid double for key '{}', using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (Exception e) {
            log.warn("[AppConfig] Invalid long for key '{}', using default {}", key, defaultValue);
            return defaultValue;
        }
    }
}
