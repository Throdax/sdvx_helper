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

    private static final String WIKI_URL_OLD = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%E6%97%A7%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";
    private static final String WIKI_URL_NEW = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%E6%96%B0%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";

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
     * Progress callbacks are invoked from the background thread. Implementors that
     * update JavaFX controls must wrap the calls in {@code Platform.runLater}.
     * </p>
     *
     * @param onProgress
     *            receives a {@code [0.0, 1.0]} progress fraction as pages load
     * @param onStatusText
     *            receives a human-readable status line (e.g.
     *            {@code "Loading BemaniWiki… 120 songs"})
     * @param onComplete
     *            receives the sorted list of parsed songs when loading finishes
     */
    public void loadAsync(Consumer<Double> onProgress, Consumer<String> onStatusText,
            Consumer<List<WikiSongRow>> onComplete) {
        bgExecutor.submit(() -> {
            Map<String, WikiSongRow> collected = new HashMap<>();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

            fetchAcList(http, WIKI_URL_OLD, collected, 1, 0.0, 0.5, onProgress, onStatusText);
            fetchAcList(http, WIKI_URL_NEW, collected, 2, 0.5, 1.0, onProgress, onStatusText);

            List<WikiSongRow> rows = new ArrayList<>(collected.values());
            rows.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
            onComplete.accept(rows);
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void fetchAcList(HttpClient http, String url, Map<String, WikiSongRow> out, int urlIndex,
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
