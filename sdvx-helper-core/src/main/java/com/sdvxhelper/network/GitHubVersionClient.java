package com.sdvxhelper.network;

import com.sdvxhelper.util.VersionUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for new application versions by querying the GitHub releases API.
 *
 * <p>Replaces the GitHub version-check logic in the Python {@code sdvx_utils.py} file.
 * Falls back to HTML tag scraping if the API rate limit is reached.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class GitHubVersionClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubVersionClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern VERSION_PATTERN = Pattern.compile("v?([0-9]+\\.[0-9]+(\\.[0-9]+)?)");

    private final String repoOwner;
    private final String repoName;
    private final HttpClient http;

    /**
     * Constructs a GitHub version client.
     *
     * @param repoOwner GitHub repository owner (e.g. {@code "ksmdvx"})
     * @param repoName  GitHub repository name (e.g. {@code "sdvx_helper"})
     */
    public GitHubVersionClient(String repoOwner, String repoName) {
        this.repoOwner = repoOwner;
        this.repoName  = repoName;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Returns the latest release version string from GitHub.
     *
     * @return version string (e.g. {@code "2.1.0"}), or {@code null} if not found
     */
    public String getLatestVersion() {
        // Try the GitHub releases API first
        Optional<String> apiVersion = queryApi();
        if (apiVersion.isPresent()) return apiVersion.get();

        // Fall back to HTML scraping
        return scrapeLatestTag().orElse(null);
    }

    /**
     * Returns {@code true} if a newer version is available on GitHub than the
     * given current version.
     *
     * @param currentVersion the version string of the running application
     * @return {@code true} if an update is available
     */
    public boolean isUpdateAvailable(String currentVersion) {
        String latest = getLatestVersion();
        if (latest == null) {
            log.debug("Could not determine latest version; assuming up to date");
            return false;
        }
        boolean newer = VersionUtil.isNewerVersion(currentVersion, latest);
        log.info("Version check: current={}, latest={}, update available={}", currentVersion, latest, newer);
        return newer;
    }

    private Optional<String> queryApi() {
        String url = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                // Extract "tag_name" from the JSON response without a JSON library dependency
                Matcher m = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
                if (m.find()) {
                    return Optional.of(normalizeVersion(m.group(1)));
                }
            }
        } catch (Exception e) {
            log.debug("GitHub API query failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> scrapeLatestTag() {
        String url = "https://github.com/" + repoOwner + "/" + repoName + "/releases";
        try {
            Document doc = Jsoup.connect(url).timeout((int) TIMEOUT.toMillis()).get();
            String firstTag = doc.select("a[href*=/releases/tag/]").stream()
                    .map(el -> el.attr("href"))
                    .filter(href -> href.contains("/releases/tag/"))
                    .findFirst()
                    .map(href -> href.substring(href.lastIndexOf('/') + 1))
                    .orElse(null);
            if (firstTag != null) {
                return Optional.of(normalizeVersion(firstTag));
            }
        } catch (IOException e) {
            log.debug("GitHub tag scraping failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static String normalizeVersion(String raw) {
        Matcher m = VERSION_PATTERN.matcher(raw);
        return m.find() ? m.group(1) : raw;
    }
}
