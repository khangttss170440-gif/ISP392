package util;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Environment > JVM properties > external UTF-8 file > shared non-secret defaults. */
public final class AppConfig {
    private static final Properties DEFAULTS = loadDefaults();
    private static final Properties LOCAL = loadExternal();

    private AppConfig() { }

    public static String get(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        if (value == null || value.isBlank()) value = LOCAL.getProperty(key);
        if (value == null || value.isBlank()) value = DEFAULTS.getProperty(key, "");
        return value.trim();
    }

    public static String require(String key) {
        String value = get(key);
        if (value.isEmpty()) throw new IllegalStateException("Missing configuration: " + key);
        return value;
    }

    public static int integer(String key, int min, int max) {
        try {
            int value = Integer.parseInt(require(key));
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalStateException("Invalid integer configuration: " + key);
    }

    public static BigDecimal decimal(String key) {
        try {
            BigDecimal value = new BigDecimal(require(key));
            if (value.signum() >= 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalStateException("Invalid nonnegative decimal configuration: " + key);
    }

    public static boolean bool(String key) {
        String value = require(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalStateException("Invalid boolean configuration: " + key);
    }

    public static List<String> list(String key) {
        return Arrays.asList(require(key).split("\\s*\\|\\s*"));
    }

    public static String httpsUrl(String key) {
        String value = require(key);
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null) return value;
        } catch (IllegalArgumentException ignored) { }
        throw new IllegalStateException("Invalid HTTPS configuration: " + key);
    }

    public static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(require("APP_LOCALE")));
        format.setCurrency(java.util.Currency.getInstance(require("APP_CURRENCY")));
        format.setMaximumFractionDigits(0);
        return format.format(value);
    }

    public static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static Properties loadDefaults() {
        Properties values = new Properties();
        try (java.io.InputStream input = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (input == null) throw new IllegalStateException("Missing application.properties resource.");
            values.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            return values;
        } catch (IOException error) {
            throw new IllegalStateException("Could not load application defaults.", error);
        }
    }

    private static Properties loadExternal() {
        String location = System.getenv("MENSFORM_CONFIG_FILE");
        if (location == null || location.isBlank()) location = System.getProperty("MENSFORM_CONFIG_FILE", "");
        Path path = location.isBlank() ? discoverExternalFile() : Path.of(location);
        Properties values = new Properties();
        if (path == null) return values;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
            return values;
        } catch (IOException error) {
            throw new IllegalStateException("Could not read external application configuration.", error);
        }
    }

    private static Path discoverExternalFile() {
        String serverBase = System.getProperty("catalina.base", "");
        if (!serverBase.isBlank()) {
            Path serverConfig = Path.of(serverBase, "conf", "mensform.properties");
            if (Files.isRegularFile(serverConfig)) return serverConfig;
        }

        Path workingFile = Path.of("config", "application.local.properties");
        if (Files.isRegularFile(workingFile)) return workingFile;

        // NetBeans runs Tomcat from its own bin directory, while classes remain
        // in the project's build/web/WEB-INF/classes. Locate the owning project
        // without storing an absolute workstation path or packaging credentials.
        java.security.CodeSource source = AppConfig.class.getProtectionDomain().getCodeSource();
        if (source == null || !"file".equalsIgnoreCase(source.getLocation().getProtocol())) return null;
        try {
            Path deployed = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            for (Path directory = deployed; directory != null; directory = directory.getParent()) {
                if (Files.isRegularFile(directory.resolve("build.xml"))
                        && Files.isDirectory(directory.resolve("src/java"))) {
                    Path projectConfig = directory.resolve("config/application.local.properties");
                    return Files.isRegularFile(projectConfig) ? projectConfig : null;
                }
            }
            return null;
        } catch (java.net.URISyntaxException error) {
            throw new IllegalStateException("Could not locate the deployed application configuration.", error);
        }
    }
}
