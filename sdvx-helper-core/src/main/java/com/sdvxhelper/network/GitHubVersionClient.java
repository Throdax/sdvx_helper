package com.sdvxhelper.network;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import com.sdvxhelper.util.VersionUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks for new application versions by querying the GitHub releases API.
 *
 * <p>
 * Replaces the GitHub version-check logic in the Python {@code sdvx_utils.py}
 * file. Falls back to HTML tag scraping if the API rate limit is reached.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class GitHubVersionClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubVersionClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern VERSION_PATTERN = Pattern.compile("v?(\\d+\\.\\d+(\\.\\d+)?)");

    private final String repoOwner;
    private final String repoName;
    private final HttpService http;

    /**
     * Constructs a GitHub version client.
     *
     * @param repoOwner
     *            GitHub repository owner (e.g. {@code "ksmdvx"})
     * @param repoName
     *            GitHub repository name (e.g. {@code "sdvx_helper"})
     */
    public GitHubVersionClient(String repoOwner, String repoName) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.http = new HttpService(TIMEOUT);
    }

    /**
     * Returns the latest release version string from GitHub.
     *
     * @return version string (e.g. {@code "2.1.0"}), or {@code null} if not found
     */
    public String getLatestVersion() {
        // Try the GitHub releases API first
        Optional<String> apiVersion = queryApi();
        if (apiVersion.isPresent())
            return apiVersion.get();

        // Fall back to HTML scraping
        return scrapeLatestTag().orElse(null);
    }

    /**
     * Returns the direct download URL for the distribution ZIP of the given release
     * tag.
     *
     * <p>
     * The URL follows the GitHub releases asset convention and points to the
     * {@code sdvx_helper_en_all.zip} file produced by the {@code native} Maven
     * profile.
     * </p>
     *
     * @param tag
     *            release tag string (e.g. {@code "2.1.0"} or {@code "v2.1.0"})
     * @return direct download URL for the distribution ZIP
     */
    public String getDownloadUrl(String tag) {
        String normalizedTag = tag.startsWith("v") ? tag : "v" + tag;
        return "https://github.com/" + repoOwner + "/" + repoName + "/releases/download/" + normalizedTag
                + "/sdvx_helper_en_all.zip";
    }

    /**
     * Returns {@code true} if a newer version is available on GitHub than the given
     * current version.
     *
     * @param currentVersion
     *            the version string of the running application
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
        try {
            URI uri = new URI("https", "api.github.com", "/repos/" + repoOwner + "/" + repoName + "/releases/latest",
                    null, null);
            HttpResponse<String> resp = http.get(uri, Map.of("Accept", "application/vnd.github+json"));
            if (resp.statusCode() == 200) {
                try (JsonReader reader = Json.createReader(new StringReader(resp.body()))) {
                    JsonObject json = reader.readObject();
                    if (json.containsKey("tag_name")) {
                        return Optional.of(normalizeVersion(json.getString("tag_name")));
                    }
                }
            }
        } catch (URISyntaxException | IOException e) {
            log.debug("GitHub API query failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> scrapeLatestTag() {
        try {
            URI uri = new URI("https", "github.com", "/" + repoOwner + "/" + repoName + "/releases", null, null);

            Document doc = Jsoup.connect(uri.toString()).timeout((int) TIMEOUT.toMillis()).get();
            String firstTag = doc.select("a[href*=/releases/tag/]").stream().map(el -> el.attr("href"))
                    .filter(href -> href.contains("/releases/tag/")).findFirst()
                    .map(href -> href.substring(href.lastIndexOf('/') + 1)).orElse(null);
            if (firstTag != null) {
                return Optional.of(normalizeVersion(firstTag));
            }
        } catch (URISyntaxException | IOException e) {
            log.debug("GitHub tag scraping failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static String normalizeVersion(String raw) {
        Matcher m = VERSION_PATTERN.matcher(raw);
        return m.find() ? m.group(1) : raw;
    }
}
