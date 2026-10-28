package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Starts fresh JVMs outside the project to reproduce NetBeans/Tomcat configuration discovery. */
public final class ConfigDiscoveryRegressionTest {
    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory(Path.of("test/.scratch"), "discovery-").toAbsolutePath();
        Path project = fixture.resolve("project");
        Path classes = project.resolve("build/web/WEB-INF/classes");
        Path unrelated = Files.createDirectories(fixture.resolve("server-bin"));
        Files.createDirectories(project.resolve("src/java"));
        Files.createDirectories(project.resolve("config"));
        Files.createDirectories(classes.resolve("util"));
        Files.writeString(project.resolve("build.xml"), "<project/>");
        Files.writeString(project.resolve("config/application.local.properties"), "AUDIT_DISCOVERY=project\n");
        copyResource("/application.properties", classes.resolve("application.properties"));
        copyResource("/util/AppConfig.class", classes.resolve("util/AppConfig.class"));
        copyResource("/util/ConfigDiscoveryRegressionTest.class", classes.resolve("util/ConfigDiscoveryRegressionTest.class"));
        copyResource("/util/ConfigDiscoveryRegressionTest$Probe.class", classes.resolve("util/ConfigDiscoveryRegressionTest$Probe.class"));

        runProbe(classes, unrelated, null, null, "project", true);
        Path explicit = fixture.resolve("explicit.properties");
        Files.writeString(explicit, "AUDIT_DISCOVERY=explicit\n");
        runProbe(classes, unrelated, explicit, null, "explicit", true);
        runProbe(classes, unrelated, fixture.resolve("missing.properties"), null, "project", false);
        Path server = fixture.resolve("tomcat");
        Files.createDirectories(server.resolve("conf"));
        Files.writeString(server.resolve("conf/mensform.properties"), "AUDIT_DISCOVERY=server\n");
        runProbe(classes, unrelated, null, server, "server", true);
        System.out.println("Configuration discovery regression checks passed: 4");
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream input = ConfigDiscoveryRegressionTest.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test resource: " + resource);
            Files.copy(input, target);
        }
    }

    private static void runProbe(Path classes, Path cwd, Path explicit, Path server,
            String expected, boolean success) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (server != null) command.add("-Dcatalina.base=" + server);
        command.addAll(List.of("-cp", classes.toString(), Probe.class.getName(), expected));
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
        builder.environment().remove("AUDIT_DISCOVERY");
        builder.environment().remove("MENSFORM_CONFIG_FILE");
        if (explicit != null) builder.environment().put("MENSFORM_CONFIG_FILE", explicit.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int code = process.waitFor();
        if ((code == 0) != success) throw new AssertionError("Configuration discovery failed: " + output);
    }

    public static final class Probe {
        public static void main(String[] args) {
            if (!args[0].equals(AppConfig.require("AUDIT_DISCOVERY")))
                throw new AssertionError("Wrong external configuration source");
        }
    }
}
