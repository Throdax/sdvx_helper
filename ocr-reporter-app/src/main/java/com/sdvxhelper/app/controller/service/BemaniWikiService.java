package com.sdvxhelper.app.controller.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.sdvxhelper.app.controller.OcrReporterHelper;
import com.sdvxhelper.app.controller.model.WikiSongRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches and parses the SOUND VOLTEX song list from BemaniWiki in a background
 * thread, reporting incremental progress through callbacks.
 *
 * <p>
 * This service has no JavaFX dependency; all UI updates are performed by the
 * caller's callback implementations which may wrap calls in
 * {@code Platform.runLater} as needed.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class BemaniWikiService {

    private static final Logger log = LoggerFactory.getLogger(BemaniWikiService.class);

    /**
     * コナステ/SOUND VOLTEX EXCEED GEAR — 全曲リスト (all-songs list).
     * Single page that supersedes the two separate AC old/new-song pages.
     */
    private static final String WIKI_URL_FULL = "https://bemaniwiki.com/index.php?%E3%82%B3%E3%83%8A%E3%82%B9%E3%83%86/SOUND+VOLTEX+EXCEED+GEAR/%E5%85%A8%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";

    /**
     * Legacy AC song-list URLs (旧曲リスト / 新曲リスト).
     * Kept for reference; no longer used by {@link #loadAsync}.
     */
    private static final String WIKI_URL_AC_OLD = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%E6%97%A7%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";
    private static final String WIKI_URL_AC_NEW = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%E6%96%B0%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";
    private ExecutorService bgExecutor;

    /**
     * @param bgExecutor
     *            executor used to run the background fetch task
     */
    public BemaniWikiService(ExecutorService bgExecutor) {
        this.bgExecutor = bgExecutor;
    }

    /**
     * Starts an asynchronous fetch of the BemaniWiki song list.
     *
     * <p>
     * Fetches three pages in sequence:
     * </p>
     * <ol>
     * <li>コナステ 全曲リスト — primary source, takes precedence on any conflict.</li>
     * <li>AC 旧曲リスト — supplemental AC tracks not on コナステ.</li>
     * <li>AC 新曲リスト — supplemental AC tracks not on コナステ.</li>
     * </ol>
     * <p>
     * Duplicate detection is whitespace-insensitive: before checking whether a
     * track already exists, all whitespace characters are stripped from the title.
     * When an AC title matches a コナステ title (with or without whitespace
     * differences), the コナステ version is kept and the conflict is logged.
     * </p>
     * <p>
     * Progress callbacks are invoked from the background thread. Implementors that
     * update JavaFX controls must wrap the calls in {@code Platform.runLater}.
     * </p>
     *
     * @param onProgress
     *            receives a {@code [0.0, 1.0]} progress fraction as pages load
     * @param onStatusText
     *            receives a human-readable status line (e.g.
     *            {@code "Loading BemaniWiki... 120 songs"})
     * @param onComplete
     *            receives the sorted list of parsed songs when loading finishes
     */
    public void loadAsync(Consumer<Double> onProgress, Consumer<String> onStatusText,
            Consumer<List<WikiSongRow>> onComplete) {
        bgExecutor.submit(() -> {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

            // Primary source: konasute all-song list
            Map<String, WikiSongRow> konasuteSongs = new HashMap<>();
            fetchFullList(http, WIKI_URL_FULL, konasuteSongs, 0.0, 0.5, onProgress, onStatusText);

            // Supplemental: AC old-song and new-song lists
            Map<String, WikiSongRow> acSongs = new HashMap<>();
            fetchAcListLegacy(http, WIKI_URL_AC_OLD, acSongs, 1, 0.5, 0.75, onProgress, onStatusText);
            fetchAcListLegacy(http, WIKI_URL_AC_NEW, acSongs, 2, 0.75, 1.0, onProgress, onStatusText);

            // Merge AC songs into konasute map; konasute wins on conflict
            mergeAcSongs(konasuteSongs, acSongs);

            List<WikiSongRow> rows = new ArrayList<>(konasuteSongs.values());
            rows.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
            onComplete.accept(rows);
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Merges AC songs into the konasute song map using whitespace-insensitive
     * title comparison.
     *
     * <p>
     * Algorithm:
     * </p>
     * <ol>
     * <li>Build a lookup table: {@code normalizedTitle -> originalKonasuteTitle}
     * from the current konasute map.</li>
     * <li>For each AC song: strip all whitespace from its title and check the
     * lookup table.</li>
     * <li>If a match is found the konasute version is kept. When the titles differ
     * only by whitespace the conflict is logged at INFO level.</li>
     * <li>If no match is found the AC song is added to the konasute map.</li>
     * </ol>
     *
     * @param base
     *            konasute song map (mutated in place)
     * @param additions
     *            AC songs to merge in
     */
    private void mergeAcSongs(Map<String, WikiSongRow> base, Map<String, WikiSongRow> additions) {
        Map<String, String> normalizedToBase = new HashMap<>();
        for (String title : base.keySet()) {
            normalizedToBase.put(stripWhitespace(title), title);
        }

        for (Map.Entry<String, WikiSongRow> entry : additions.entrySet()) {
            String acTitle = entry.getKey();
            String normalized = stripWhitespace(acTitle);
            if (normalizedToBase.containsKey(normalized)) {
                String baseTitle = normalizedToBase.get(normalized);
                if (!acTitle.equals(baseTitle)) {
                    log.info("mergeAcSongs: AC title '{}' matches konasute title '{}' (whitespace difference) - konasute takes precedence",
                            acTitle, baseTitle);
                }
            } else {
                base.put(acTitle, entry.getValue());
                normalizedToBase.put(normalized, acTitle);
            }
        }
    }

    /**
     * Removes all whitespace characters from {@code title} for use as a
     * duplicate-detection key.
     *
     * @param title
     *            raw title, may be {@code null}
     * @return title with every whitespace character stripped, or an empty string
     *         if {@code title} is {@code null}
     */
    private static String stripWhitespace(String title) {
        return title == null ? "" : title.replaceAll("\\s+", "");
    }

    /**
     * Parses the コナステ 全曲リスト (all-songs) page.
     *
     * <p>
     * Table column layout (8 cells per full row):
     * </p>
     * <pre>
     *   0: 曲名 (title)
     *   1: アーティスト (artist)  — may be absent when rowspanned from a prior row
     *   2: BPM
     *   3: NOV level
     *   4: ADV level
     *   5: EXH level
     *   6: APPEND-class level (INF/GRV/HVN/VVD/XCD/MXM — empty or "-" when absent)
     *   7: 配信日 (release date, ignored)
     * </pre>
     * <p>
     * When the artist cell carries a {@code rowspan} attribute the following
     * rows have only 7 cells; an {@code offset = -1} is applied so that the
     * difficulty indices still resolve correctly.
     * </p>
     */
    private void fetchFullList(HttpClient http, String url, Map<String, WikiSongRow> out,
            double progressStart, double progressEnd, Consumer<Double> onProgress, Consumer<String> onStatusText) {
        try {
            String html = fetchUrl(http, url);
            if (html == null) {
                log.debug("fetchFullList: HTTP response was null for '{}', skipping parse", url);
                return;
            }
            Document doc = Jsoup.parse(html);
            int cntRowspanArtist = 0;
            String preArtist = "";

            Elements allTrs = doc.select("tr");
            int totalTrs = allTrs.size();
            for (int trIdx = 0; trIdx < totalTrs; trIdx++) {
                Element tr = allTrs.get(trIdx);

                if (trIdx % 10 == 0 || trIdx == totalTrs - 1) {
                    double prog = totalTrs == 0
                            ? progressEnd
                            : progressStart + (progressEnd - progressStart) * trIdx / totalTrs;
                    onProgress.accept(prog);
                    onStatusText.accept("Loading BemaniWiki... " + out.size() + " songs");
                }

                Elements tds = tr.select("td");
                int n = tds.size();

                // Full row = 8 cells; artist-rowspan row = 7 cells.
                // Sub-header colspan rows (GRV / HVN / VVD / XCD / MXM) have n=1.
                // Header rows using <th> have n=0.  All others are skipped.
                if (n != 8 && n != 7) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    continue;
                }

                // Skip column-header rows where the BPM cell (index 2) reads "BPM"
                if ("BPM".equals(tds.get(2).text())) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    continue;
                }

                String title = tds.get(0).text();
                String artist = tds.get(1).text();
                String bpm = tds.get(2).text();

                // offset compensates for the missing artist cell in rowspan rows
                int offset = 0;
                Element artistTd = tds.get(1);
                if (artistTd.hasAttr("rowspan")) {
                    cntRowspanArtist = Integer.parseInt(artistTd.attr("rowspan"));
                    preArtist = artistTd.text();
                } else if (cntRowspanArtist > 0) {
                    offset = -1;
                    artist = preArtist;
                    bpm = tds.get(1).text();
                }

                // Skip sub-header rows that have no artist and no BPM (e.g. grade banners
                // rendered as 8 empty cells rather than a colspan cell)
                if (artist.isEmpty() && bpm.isEmpty()) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    continue;
                }

                // Skip rows where NOV is absent — indicates a difficulty-legend row
                // or a song with no NOV chart (filter rather than collect garbage)
                Element novTd = tds.get(3 + offset);
                if ("-".equals(novTd.text()) || novTd.text().isEmpty()) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    continue;
                }

                String nov = OcrReporterHelper.lastDigits(tds.get(3 + offset).text());
                String adv = OcrReporterHelper.lastDigits(tds.get(4 + offset).text());
                String exh = OcrReporterHelper.lastDigits(tds.get(5 + offset).text());
                String appendTxt = tds.get(6 + offset).text();
                String append = (appendTxt.isEmpty() || "-".equals(appendTxt))
                        ? null
                        : OcrReporterHelper.lastDigits(appendTxt);

                if (!out.containsKey(title)) {
                    out.put(title, new WikiSongRow(title, artist, bpm, nov, adv, exh, append));
                }

                cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
            }
        } catch (NumberFormatException e) {
            log.warn("fetchFullList: failed to parse full wiki list: {}", e.getMessage());
        }
    }

    /**
     * Legacy parser for the AC 旧曲リスト / 新曲リスト pages.
     *
     * <p>
     * Kept for reference. No longer called by {@link #loadAsync}; use
     * {@link #fetchFullList} with {@link #WIKI_URL_FULL} instead.
     * </p>
     */
    private void fetchAcListLegacy(HttpClient http, String url, Map<String, WikiSongRow> out, int urlIndex,
            double progressStart, double progressEnd, Consumer<Double> onProgress, Consumer<String> onStatusText) {
        try {
            String html = fetchUrl(http, url);
            if (html == null) {
                log.debug("fetchAcList: HTTP response was null for URL '{}', skipping parse", url);
                return;
            }
            Document doc = Jsoup.parse(html);
            int cntRowspanArtist = 0;
            int cntRowspanBpm = 0;
            String preArtist = "";
            String preBpm = "";

            Elements allTrs = doc.select("tr");
            int totalTrs = allTrs.size();
            for (int trIdx = 0; trIdx < totalTrs; trIdx++) {
                Element tr = allTrs.get(trIdx);

                if (trIdx % 10 == 0 || trIdx == totalTrs - 1) {
                    double prog = totalTrs == 0
                            ? progressEnd
                            : progressStart + (progressEnd - progressStart) * trIdx / totalTrs;
                    onProgress.accept(prog);
                    onStatusText.accept("Loading BemaniWiki… " + out.size() + " songs");
                }

                Elements tds = tr.select("td");
                int n = tds.size();

                int titleFlg = 0;
                int rowspanFlg = 0;
                if (!tds.isEmpty() && tds.get(0).text().matches("\\d{4}/\\d{2}/\\d{2}.*")) {
                    titleFlg = 1;
                    rowspanFlg = 1;
                }

                if (n != 7 + rowspanFlg && n != 8 + rowspanFlg) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
                    continue;
                }
                if ("BPM".equals(tds.get(3).text())) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
                    continue;
                }

                String title = tds.get(0 + titleFlg).text();
                if (tds.get(0).text().matches("\\d{4}/\\d{2}/\\d{2}")) {
                    title = tds.get(1).text();
                }
                String artist = tds.get(1 + titleFlg).text();
                String bpm = tds.get(2 + titleFlg).text();

                Element artistTd = tds.get(1 + titleFlg);
                if (artistTd.hasAttr("rowspan")) {
                    cntRowspanArtist = Integer.parseInt(artistTd.attr("rowspan"));
                    preArtist = artistTd.text();
                } else if (cntRowspanArtist > 0) {
                    rowspanFlg -= 1;
                    artist = preArtist;
                    bpm = tds.get(1 + titleFlg).text();
                }

                Element bpmTd = tds.get(2 + titleFlg);
                if (bpmTd.hasAttr("rowspan")) {
                    cntRowspanBpm = Integer.parseInt(bpmTd.attr("rowspan"));
                    preBpm = bpmTd.text();
                } else if (cntRowspanBpm > 0) {
                    rowspanFlg -= 1;
                    bpm = preBpm;
                }

                Element novTd = tds.get(3 + rowspanFlg);
                if ("-".equals(novTd.text())) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
                    continue;
                }

                String nov = OcrReporterHelper.lastDigits(tds.get(3 + rowspanFlg).text());
                String adv = OcrReporterHelper.lastDigits(tds.get(4 + rowspanFlg).text());
                String exh = OcrReporterHelper.lastDigits(tds.get(5 + rowspanFlg).text());
                String appendTxt = tds.get(6 + rowspanFlg).text();
                String append = (appendTxt.isEmpty() || "-".equals(appendTxt))
                        ? null
                        : OcrReporterHelper.lastDigits(appendTxt);

                if (!out.containsKey(title)) {
                    out.put(title, new WikiSongRow(title, artist, bpm, nov, adv, exh, append));
                }

                cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to fetch AC wiki list {}: {}", urlIndex, e.getMessage());
        }
    }

    private String fetchUrl(HttpClient http, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "sdvx-helper/2.0").GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            log.warn("HTTP {} for {}", resp.statusCode(), url);
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to fetch {}: {}", url, e.getMessage());
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
