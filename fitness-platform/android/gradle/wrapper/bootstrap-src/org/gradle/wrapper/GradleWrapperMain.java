package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal, dependency-free Gradle distribution bootstrap.
 *
 * <p>The official Gradle wrapper could not be generated in the isolated scaffold environment. This
 * source remains in the repository so the small bootstrap JAR is auditable. It downloads the pinned
 * distribution, verifies distributionSha256Sum, protects against zip-slip, and delegates to Gradle's
 * launcher. Regenerate the standard wrapper with `gradle wrapper` once Gradle is available.</p>
 */
public final class GradleWrapperMain {
    private static final int MAX_REDIRECTS = 8;

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path wrapperJar = Paths.get(GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path projectRoot = wrapperJar.getParent().getParent().getParent();
        Path propertiesPath = wrapperJar.getParent().resolve("gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath, new OpenOption[0])) {
            properties.load(input);
        }

        String distributionUrl = Objects.requireNonNull(
                properties.getProperty("distributionUrl"), "distributionUrl").replace("\\:", ":");
        String expectedSha256 = Objects.requireNonNull(
                properties.getProperty("distributionSha256Sum"), "distributionSha256Sum").trim();
        String gradleUserHome = System.getenv().getOrDefault(
                "GRADLE_USER_HOME", System.getProperty("user.home") + java.io.File.separator + ".gradle");
        String cacheKey = sha256(distributionUrl.getBytes(StandardCharsets.UTF_8)).substring(0, 20);
        Path installDir = Paths.get(gradleUserHome, "wrapper", "dists", "fitness-platform", cacheKey);
        Path marker = installDir.resolve(".installed");

        if (!Files.exists(marker, new LinkOption[0])) {
            installDistribution(distributionUrl, expectedSha256, installDir, marker);
        }

        Path launcher = findLauncher(installDir);
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());
        command.add("-Dorg.gradle.appname=gradlew");
        command.add("-classpath");
        command.add(launcher.toString());
        command.add("org.gradle.launcher.GradleMain");
        command.addAll(Arrays.asList(args));

        Process process = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .inheritIO()
                .start();
        System.exit(process.waitFor());
    }

    private static void installDistribution(
            String distributionUrl,
            String expectedSha256,
            Path installDir,
            Path marker) throws Exception {
        Files.createDirectories(installDir, new FileAttribute<?>[0]);
        Path archive = installDir.resolve("distribution.zip.part");
        System.out.println("Downloading pinned Gradle distribution: " + distributionUrl);
        downloadFollowingRedirects(new URL(distributionUrl), archive, 0);

        String actualSha256 = sha256(Files.readAllBytes(archive));
        if (!MessageDigest.isEqual(
                actualSha256.getBytes(StandardCharsets.US_ASCII),
                expectedSha256.getBytes(StandardCharsets.US_ASCII))) {
            Files.deleteIfExists(archive);
            throw new SecurityException(
                    "Gradle distribution checksum mismatch. Expected " + expectedSha256 + " but got " + actualSha256);
        }

        unzip(archive, installDir);
        Files.deleteIfExists(archive);
        Files.createFile(marker, new FileAttribute<?>[0]);
    }

    private static void downloadFollowingRedirects(URL url, Path target, int redirectCount) throws IOException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("Too many redirects while downloading Gradle");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        connection.setRequestProperty("User-Agent", "fitness-platform-gradle-bootstrap/1");
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null) {
                throw new IOException("Redirect did not contain a Location header");
            }
            downloadFollowingRedirects(new URL(url, location), target, redirectCount + 1);
            return;
        }
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("Gradle download failed with HTTP " + status);
        }
        try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedDestination)) {
                    throw new SecurityException("Blocked zip-slip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output, new FileAttribute<?>[0]);
                } else {
                    Files.createDirectories(output.getParent(), new FileAttribute<?>[0]);
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static Path findLauncher(Path installDir) throws IOException {
        try (Stream<Path> paths = Files.walk(installDir, Integer.MAX_VALUE, new FileVisitOption[0])) {
            return paths
                    .filter(path -> path.getFileName().toString().startsWith("gradle-launcher-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Gradle launcher JAR not found in " + installDir));
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
