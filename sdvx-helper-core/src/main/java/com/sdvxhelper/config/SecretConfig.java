package com.sdvxhelper.config;

/**
 * Provides access to sensitive configuration values that must not be committed to
 * version control.
 *
 * <p>Secrets are loaded from environment variables first, then from a
 * {@code secrets.properties} file in the working directory (excluded via
 * {@code .gitignore}).  This replaces the Python {@code params_secret.py} file.</p>
 *
 * <p>Required keys:</p>
 * <ul>
 *   <li>{@code MAYA2_KEY} – HMAC key used to sign Maya2 upload payloads.</li>
 * </ul>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class SecretConfig {

    private static final String ENV_MAYA2_KEY = "MAYA2_KEY";
    private static final String PROP_FILE = "secrets.properties";

    private final String maya2Key;

    /**
     * Loads secrets from environment variables and, if not found there, from
     * {@code secrets.properties} in the current working directory.
     */
    public SecretConfig() {
        this.maya2Key = resolve(ENV_MAYA2_KEY);
    }

    /**
     * Returns the Maya2 HMAC key.
     *
     * @return Maya2 key string, or an empty string if not configured
     */
    public String getMaya2Key() {
        return maya2Key != null ? maya2Key : "";
    }

    /**
     * Resolves a secret value by checking (in order):
     * <ol>
     *   <li>JVM system property ({@code -Dkey=value})</li>
     *   <li>OS environment variable</li>
     *   <li>{@code secrets.properties} file in the working directory</li>
     * </ol>
     *
     * @param key property/environment variable name
     * @return resolved value, or {@code null} if not found anywhere
     */
    private static String resolve(String key) {
        // 1. JVM system property
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // 2. OS environment variable
        value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // 3. secrets.properties file
        java.io.File propFile = new java.io.File(PROP_FILE);
        if (propFile.exists()) {
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(propFile)) {
                props.load(fis);
                value = props.getProperty(key);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            } catch (java.io.IOException ignored) {
                // fall through to return null
            }
        }

        return null;
    }
}
