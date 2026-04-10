---
name: Python to Java Migration
overview: Migrate the SDVX Helper Python application (20+ Python files, PySimpleGUI, PIL/imagehash, OBS WebSocket, Discord RPC, EasyOCR) to a Java 25 Maven project using JavaFX for UI, JPackage for distribution, Maven Shade for uber JAR, and JAXB for converting pickle data to XML-backed POJOs.
todos:
  - id: phase0-relocate
    content: "Phase 0: Move all existing Python source files, resources/, and build scripts into a `python/` subdirectory. Update build.cmd and build.sh paths so PyInstaller builds still work from within `python/`. Update .gitignore accordingly."
    status: completed
  - id: phase1-maven
    content: "Phase 1a: Create multi-module Maven project (parent POM + sdvx-helper-core + 5 app modules) with dependencies (Java 25, JavaFX, JAXB, JSON-B/JSON-P with Eclipse Yasson, Jsoup, Tess4J, JNativeHook, SLF4J + Log4J2, JUnit 5, Mockito, Shade plugin, JPackage plugin)"
    status: completed
  - id: phase1-models
    content: "Phase 1b: Implement all domain model POJOs with JAXB annotations (OnePlayData, MusicInfo, OneLevelStat, Stats, PlayLog, MusicList, RivalLog, TitleConvTable, SongInfo) + enums (GuiMode, DetectMode, ScoreRank, PlayState, DisplayMode) + Javadoc + unit tests"
    status: completed
  - id: phase1-config
    content: "Phase 1c: Implement config (DefaultSettings, SecretConfig), i18n (MessageService wrapping java.util.ResourceBundle), util (VersionUtil, SpecialTitles, ScoreFormatter) + Javadoc + unit tests"
    status: completed
  - id: phase2-repos
    content: "Phase 2a: Implement repositories (PlayLogRepository, MusicListRepository, RivalLogRepository, TitleConvTableRepository, SettingsRepository, ParamsRepository) with JAXB/JSON-B (Yasson) + Javadoc + unit tests"
    status: completed
  - id: phase2-migration
    content: "Phase 2b: Create Python pickle-to-XML migration script (migrate_pkl_to_xml.py) that exports alllog.pkl, musiclist.pkl, rival_log.pkl, title_conv_table.pkl to the new XML schema"
    status: completed
  - id: phase3-services
    content: "Phase 3: Implement core services (VolforceCalculator, SdvxLoggerService, ImageAnalysisService, PerceptualHasher, ScoreDetector, ShaGenerator, XmlExportService, CsvExportService, AnalysisService, TitleNormalizer) + Javadoc + unit tests"
    status: completed
  - id: phase4-network
    content: "Phase 4: Implement network clients (ObsWebSocketClient, Maya2Client, DiscordPresenceClient, DiscordWebhookClient, LitterboxClient, GitHubVersionClient, GoogleDriveClient) + Javadoc + unit tests"
    status: completed
  - id: phase5-ui
    content: "Phase 5: Implement all JavaFX UIs (main app with 5 scenes, score viewer, play log sync, OCR reporter, updater) -- FXML layouts + controller classes + Javadoc"
    status: completed
  - id: phase6-build
    content: "Phase 6: Configure Maven Shade uber JARs (5) and JPackage native executables (5) for all app modules, build scripts, verify end-to-end packaging and distribution zip"
    status: completed
  - id: phase7-optimize
    content: "Phase 7 (post-migration): Externalize special_titles from compiled Java class to special_titles.json (editable without recompilation). SpecialTitlesRepository loads from file at runtime with bundled defaults as fallback."
    status: completed
isProject: false
---

# SDVX Helper: Python to Java 25 Migration Plan

## 1. Current Architecture Summary

The application is a **Sound Voltex (SDVX) game helper** that captures game screens via OBS WebSocket, detects game state using perceptual image hashing, identifies songs from a local database, tracks play history, drives OBS overlays, and optionally integrates with Discord Rich Presence, webhooks, and a Maya2 server.

**5 entry points** exist today (each built as a standalone `.exe` via PyInstaller):


| Python File                          | Role                                       |
| ------------------------------------ | ------------------------------------------ |
| [sdvx_helper.pyw](sdvx_helper.pyw)   | Main app: detection loop, OBS capture, GUI |
| [manage_score.py](manage_score.py)   | Score viewer/table GUI                     |
| [play_log_sync.py](play_log_sync.py) | Reconcile screenshots with play log        |
| [ocr_reporter.py](ocr_reporter.py)   | Maintainer tool: register unknown hashes   |
| [update.py](update.py)               | Self-updater from GitHub releases          |


---

## 2. Python Source Preservation (Phase 0)

All existing Python source files and resources will be relocated into a `python/` subdirectory at the project root. This keeps the legacy application fully functional and buildable while the Java migration proceeds alongside it.

### 2.1 New Repository Layout

```
sdvx_helper/                        # project root
├── python/                         # all legacy Python code lives here
│   ├── sdvx_helper.pyw
│   ├── sdvxh_classes.py
│   ├── gen_summary.py
│   ├── manage_score.py
│   ├── manage_settings.py
│   ├── obssocket.py
│   ├── discord_presence.py
│   ├── ocr_auto.py
│   ├── ocr_reporter.py
│   ├── play_log_sync.py
│   ├── update.py
│   ├── connect_maya2.py
│   ├── poor_man_resource_bundle.py
│   ├── sdvx_utils.py
│   ├── sha_generator.py
│   ├── special_titles.py
│   ├── hash_converter.py
│   ├── manage_pkl.py
│   ├── update_gradeS_table.py
│   ├── test_classes.py
│   ├── params_secret.py
│   ├── requirements.txt
│   ├── build.cmd
│   ├── build.sh
│   ├── icon.ico
│   ├── NotoSansJP-Regular.ttf
│   ├── version.properties
│   └── resources/                  # existing resources stay with Python code
│       ├── params.json
│       ├── i18n/
│       │   ├── messages_en.properties
│       │   └── messages_ja.properties
│       └── images/
├── pom.xml                         # parent POM (multi-module)
├── sdvx-helper-core/               # shared library module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/sdvxhelper/
│       ├── main/resources/
│       └── test/java/com/sdvxhelper/
├── sdvx-helper-app/                # main app module -> sdvx_helper.exe
│   ├── pom.xml
│   └── src/main/java/com/sdvxhelper/app/
├── score-viewer-app/               # score viewer module -> manage_score.exe
│   ├── pom.xml
│   └── src/main/java/com/sdvxhelper/app/
├── ocr-reporter-app/               # OCR reporter module -> ocr_reporter.exe
│   ├── pom.xml
│   └── src/main/java/com/sdvxhelper/app/
├── play-log-sync-app/              # play log sync module -> play_log_sync.exe
│   ├── pom.xml
│   └── src/main/java/com/sdvxhelper/app/
├── updater-app/                    # updater module -> update.exe
│   ├── pom.xml
│   └── src/main/java/com/sdvxhelper/app/
├── README.md
├── README_EN.md
├── README_JP.md
├── LICENSE
└── .gitignore
```

### 2.2 Files That Move to `python/`

All `.py`, `.pyw` files, `requirements.txt`, `build.cmd`, `build.sh`, `icon.ico`, `NotoSansJP-Regular.ttf`, `version.properties`, and the entire `resources/` directory.

Files that stay at root: `README.md`, `README_EN.md`, `README_JP.md`, `LICENSE`, `.gitignore`, and the new `pom.xml`.

### 2.3 Build Script Adjustments

Both `build.cmd` and `build.sh` must be updated so they still work from within the `python/` folder. The key changes:

`**build.cmd**` -- All PyInstaller invocations already reference files by relative name (e.g., `%target_sdvx%.pyw`), so as long as the script is run from inside `python/`, paths work naturally. The script will be invoked as:

```bat
cd python
build.cmd [args]
```

Specific path updates needed in `build.cmd`:

- No source path changes required (all `.py`/`.pyw` references are relative)
- Output paths (`dist\`, `out\`, `resources\`) are already relative and will resolve inside `python/`

`**build.sh**` -- Same principle: run from `python/` directory.

### 2.4 `.gitignore` Updates

The root `.gitignore` must be updated to reflect the new `python/` prefix for Python-specific ignores:

```gitignore
# Python build artifacts (under python/)
python/dist/*
python/build*
python/to_bin/*
python/*.spec
python/*.pyc
python/__pycache__/*
python/sdvx_helper/*
python/sdvx_*er/*
python/tmp/*
python/jackets/*
python/*.zip
python/log/*
python/params_secret.py
python/out/*
python/alllog.pkl
python/settings.json

# General
*.log
.settings/
.project
.pydevproject

# Java build artifacts
target/
```

---

## 3. Target Java Package Structure

The project uses a **multi-module Maven layout**: one shared `core` library and five thin application modules, each producing its own uber JAR and native executable.

### 3.1 Shared Core Module (`sdvx-helper-core`)

All domain models, services, repositories, networking, OCR, config, i18n, and utilities live here. This module has no `main()` method -- it is a library dependency for all 5 app modules.

```
com.sdvxhelper
├── model                       # Domain POJOs + JAXB annotations
│   └── enums                   # Enums (DetectMode, GuiMode, ScoreRank, etc.)
├── service                     # Core business logic (stateless services)
├── repository                  # Data I/O (XML, CSV, JSON, settings)
├── network                     # HTTP clients, WebSocket, Discord RPC
├── ocr                         # Image hashing, OCR, SHA generation
├── ui                          # Shared JavaFX components, base controllers
│   ├── controller
│   ├── component               # Reusable custom UI controls
│   └── view                    # Shared .fxml files
├── i18n                        # ResourceBundle management
├── config                      # Settings, defaults, secrets
└── util                        # Small utilities (version, title normalization)
```

### 3.2 Application Modules (5 modules, one per tool)

Each app module is a thin wrapper containing only the JavaFX `Application` subclass, its FXML layout(s), and its controller(s). Each depends on `sdvx-helper-core`.


| Module              | Main Class                          | Maps To            |
| ------------------- | ----------------------------------- | ------------------ |
| `sdvx-helper-app`   | `com.sdvxhelper.app.SdvxHelperApp`  | `sdvx_helper.pyw`  |
| `score-viewer-app`  | `com.sdvxhelper.app.ScoreViewerApp` | `manage_score.py`  |
| `ocr-reporter-app`  | `com.sdvxhelper.app.OcrReporterApp` | `ocr_reporter.py`  |
| `play-log-sync-app` | `com.sdvxhelper.app.PlayLogSyncApp` | `play_log_sync.py` |
| `updater-app`       | `com.sdvxhelper.app.UpdaterApp`     | `update.py`        |


---

## 4. Python-to-Java Library Mapping


| Python Library              | Java Equivalent                                                      | Maven Artifact                                                                                          | Purpose                                                 |
| --------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| PySimpleGUI / tkinter       | **JavaFX 25**                                                        | `org.openjfx:javafx-controls`, `javafx-fxml`                                                            | GUI                                                     |
| Pillow (PIL)                | **Java AWT + ImageIO** + JavaFX Image                                | JDK built-in                                                                                            | Image loading, cropping, resizing, pixel access         |
| imagehash                   | **Custom `PerceptualHash` class** (port average_hash using AWT)      | None (hand-rolled, ~50 lines with DCT from `javax.imageio`)                                             | Perceptual hashing for song identification              |
| numpy (pixel sums, sorting) | **Java AWT `Raster`** + standard arrays                              | JDK built-in                                                                                            | Pixel color channel sums, array operations              |
| scipy.stats.rankdata        | **Custom rankdata util** (trivial ~20 lines)                         | None                                                                                                    | Rank assignment for rival scores                        |
| requests                    | `**java.net.http.HttpClient`**                                       | JDK built-in (Java 11+)                                                                                 | HTTP GET/POST to Maya2, GitHub, Google Drive, litterbox |
| BeautifulSoup4              | **Jsoup**                                                            | `org.jsoup:jsoup:1.18.x`                                                                                | HTML parsing (BemaniWiki, GitHub tags)                  |
| obsws-python                | **obs-websocket-java**                                               | `io.obswebsocket:obs-websocket-java:2.x` or custom WebSocket via `java.net.http.WebSocket`              | OBS WebSocket protocol                                  |
| pypresence                  | **Custom Discord IPC client** (Unix/Windows named pipe)              | Hand-rolled or `com.jagrosh:DiscordIPC:0.4`                                                             | Discord Rich Presence                                   |
| keyboard                    | **JNativeHook**                                                      | `com.github.kwhat:jnativehook:2.2.2`                                                                    | Global keyboard hotkeys                                 |
| pyautogui                   | `**java.awt.Robot`**                                                 | JDK built-in                                                                                            | Screen coordinate operations                            |
| discord-webhook             | **Plain `HttpClient` POST**                                          | JDK built-in                                                                                            | Send Discord webhook messages                           |
| easyocr                     | **Tess4J** (Tesseract wrapper)                                       | `net.sourceforge.tess4j:tess4j:5.x`                                                                     | OCR for title/level images                              |
| pickle                      | **JAXB (Jakarta XML Binding)**                                       | `jakarta.xml.bind:jakarta.xml.bind-api`, `org.glassfish.jaxb:jaxb-runtime`                              | Serialize/deserialize domain objects as XML             |
| json (settings)             | **JSON-B (Jakarta JSON Binding) + JSON-P (Jakarta JSON Processing)** | `jakarta.json.bind:jakarta.json.bind-api`, `jakarta.json:jakarta.json-api`, `org.eclipse:yasson` (impl) | Read/write `settings.json` and `params.json`            |
| logging                     | **SLF4J + Log4J2**                                                   | `org.apache.logging.log4j:log4j-core`, `log4j-api`, `log4j-slf4j2-impl`                                 | Logging with rotating file appenders                    |
| winsound                    | `**javax.sound.sampled`**                                            | JDK built-in                                                                                            | Play WAV alerts                                         |


---

## 5. Pickle-to-XML POJO Conversion

Four pickle data structures must be converted to XML-backed POJOs:

### 5.1 `alllog.pkl` -- `List<OnePlayData>`

Currently: Python `list` of `OnePlayData` objects.

```java
@XmlRootElement(name = "PlayLog")
@XmlAccessorType(XmlAccessType.FIELD)
public class PlayLog {
    @XmlElement(name = "play")
    private List<OnePlayData> plays = new ArrayList<>();
}

@XmlAccessorType(XmlAccessType.FIELD)
public class OnePlayData implements Comparable<OnePlayData> {
    private String title;
    private int curScore;
    private int preScore;
    private String lamp;
    private String difficulty;
    private String date;
    @XmlTransient
    private int diff; // computed: curScore - preScore
}
```

Stored as: `alllog.xml`

### 5.2 `musiclist.pkl` -- Complex nested dict

Currently: Python `dict` with keys `jacket`, `info`, `jacket_sha`, `titles`, `gradeS_lv17`-`19`.

```java
@XmlRootElement(name = "MusicList")
public class MusicList {
    private Map<String, DifficultyHashes> jacket;     // diff -> (songTitle -> hash)
    private Map<String, DifficultyHashes> info;        // diff -> (songTitle -> hash)
    private Map<String, DifficultyHashes> jacketSha;   // diff -> (songTitle -> sha)
    private Map<String, SongInfo> titles;               // songTitle -> [artist,bpm,lvNov,lvAdv,lvExh,lvAppend]
    private Map<String, Map<String, String>> gradeSTable; // "lv17" -> (title -> tier)
}
```

Stored as: `musiclist.xml`

### 5.3 `rival_log.pkl` -- `Map<String, List<MusicInfo>>`

```java
@XmlRootElement(name = "RivalLog")
public class RivalLog {
    @XmlElement(name = "rival")
    private List<RivalEntry> rivals = new ArrayList<>();
}

public class RivalEntry {
    private String name;
    @XmlElement(name = "score")
    private List<MusicInfo> scores;
}
```

Stored as: `rival_log.xml`

### 5.4 `title_conv_table.pkl` -- `Map<String, String>`

```java
@XmlRootElement(name = "TitleConversionTable")
public class TitleConvTable {
    @XmlElement(name = "entry")
    private List<TitleMapping> entries;
}

public class TitleMapping {
    @XmlAttribute
    private String localTitle;
    @XmlAttribute
    private String maya2Title;
}
```

Stored as: `title_conv_table.xml`

A `**DataMigrator**` utility class will be provided that can read the old pickle files (via a Python migration script) and export them to the new XML format. The Python script `migrate_pkl_to_xml.py` will be created to do the one-time export.

---

## 6. File-by-File Migration Map

### 6.1 Domain Models (`com.sdvxhelper.model`)


| Python Source                                                                                | Java Class(es)                                                                                       | Notes                                             |
| -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `OnePlayData` in [sdvxh_classes.py](sdvxh_classes.py) L61-151                                | `model.OnePlayData`                                                                                  | JAXB-annotated, `Comparable<OnePlayData>` by date |
| `MusicInfo` in [sdvxh_classes.py](sdvxh_classes.py) L153-246                                 | `model.MusicInfo`                                                                                    | JAXB-annotated, `Comparable<MusicInfo>` by VF     |
| `OneLevelStat` in [sdvxh_classes.py](sdvxh_classes.py) L248-309                              | `model.OneLevelStat`                                                                                 | Stats per level                                   |
| `Stats` in [sdvxh_classes.py](sdvxh_classes.py) L311-334                                     | `model.Stats`                                                                                        | Aggregates 20 `OneLevelStat`                      |
| Enums `gui_mode`, `detect_mode`, `score_rank` in [sdvxh_classes.py](sdvxh_classes.py) L33-58 | `model.enums.GuiMode`, `DetectMode`, `ScoreRank`                                                     | Standard Java enums                               |
| Enums `PlayStates`, `DisplayMode` in [discord_presence.py](discord_presence.py) L35-45       | `model.enums.PlayState`, `DisplayMode`                                                               |                                                   |
| XML data containers                                                                          | `model.PlayLog`, `model.MusicList`, `model.RivalLog`, `model.TitleConvTable`, `model.SongInfo`, etc. | JAXB root elements                                |


### 6.2 Services (`com.sdvxhelper.service`)


| Python Source                                                            | Java Class                     | Responsibility                                                                                                                                                                                                    |
| ------------------------------------------------------------------------ | ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SDVXLogger` in [sdvxh_classes.py](sdvxh_classes.py) L336-1061           | `service.SdvxLoggerService`    | Play log push/pop, best computation, VF calculation, stats, CSV/XML export. **Extracted from the god-class**: VF math goes to `VolforceCalculator`, XML writing to `XmlExportService`, CSV to `CsvExportService`. |
| VF formulas in `OnePlayData.get_vf_single` and `MusicInfo.get_vf_single` | `service.VolforceCalculator`   | Stateless VF calculation shared by both models                                                                                                                                                                    |
| `GenSummary` in [gen_summary.py](gen_summary.py)                         | `service.ImageAnalysisService` | Image hash loading, score digit detection, lamp detection, OCR jacket matching, summary image generation                                                                                                          |
| Score detection (pixel hashing digits)                                   | `ocr.ScoreDetector`            | Extracted from `GenSummary.get_score` / `get_score_on_select`                                                                                                                                                     |
| XML output methods (`gen_history_cursong`, `gen_sdvx_battle`, etc.)      | `service.XmlExportService`     | Proper JAXB marshalling instead of manual string concatenation                                                                                                                                                    |
| CSV output methods (`gen_best_csv`, etc.)                                | `service.CsvExportService`     | OpenCSV or Jackson CSV                                                                                                                                                                                            |
| `analyze()` in SDVXLogger                                                | `service.AnalysisService`      | VF breakdown analysis, tweet generation                                                                                                                                                                           |


### 6.3 Repositories / Data I/O (`com.sdvxhelper.repository`)


| Python Source                           | Java Class                            | Notes                                                          |
| --------------------------------------- | ------------------------------------- | -------------------------------------------------------------- |
| `pickle.load/dump` of `alllog.pkl`      | `repository.PlayLogRepository`        | JAXB marshal/unmarshal `PlayLog`                               |
| `pickle.load/dump` of `musiclist.pkl`   | `repository.MusicListRepository`      | JAXB marshal/unmarshal `MusicList`; also handles HTTP download |
| `pickle.load/dump` of `rival_log.pkl`   | `repository.RivalLogRepository`       | JAXB marshal/unmarshal `RivalLog`                              |
| `pickle.load` of `title_conv_table.pkl` | `repository.TitleConvTableRepository` | JAXB unmarshal                                                 |
| `settings.json` read/write              | `repository.SettingsRepository`       | JSON-B (Jsonb) with Eclipse Yasson                             |
| `params.json` read                      | `repository.ParamsRepository`         | JSON-B (Jsonb) to a `DetectionParams` POJO                     |


### 6.4 Networking (`com.sdvxhelper.network`)


| Python Source                                                                                           | Java Class                      | Notes                                                                |
| ------------------------------------------------------------------------------------------------------- | ------------------------------- | -------------------------------------------------------------------- |
| `OBSSocket` in [obssocket.py](obssocket.py)                                                             | `network.ObsWebSocketClient`    | Use `obs-websocket-java` or raw `java.net.http.WebSocket` + JSON-RPC |
| `ManageMaya2` in [sdvxh_classes.py](sdvxh_classes.py) L1062-1223 / [connect_maya2.py](connect_maya2.py) | `network.Maya2Client`           | `HttpClient` for REST calls, HMAC signing for uploads                |
| `SDVXDiscordPresence` in [discord_presence.py](discord_presence.py)                                     | `network.DiscordPresenceClient` | Named pipe IPC on Windows (Discord RPC protocol)                     |
| `DiscordWebhook` usage in [gen_summary.py](gen_summary.py)                                              | `network.DiscordWebhookClient`  | Simple `HttpClient` multipart POST                                   |
| litterbox upload in [discord_presence.py](discord_presence.py)                                          | `network.LitterboxClient`       | `HttpClient` multipart POST                                          |
| GitHub version check in [sdvx_utils.py](sdvx_utils.py)                                                  | `network.GitHubVersionClient`   | `HttpClient` + Jsoup for tag parsing                                 |
| Google Drive CSV download in [sdvxh_classes.py](sdvxh_classes.py) L412-477                              | `network.GoogleDriveClient`     | `HttpClient` with session/cookie handling                            |


### 6.5 OCR / Image Processing (`com.sdvxhelper.ocr`)


| Python Source                                                        | Java Class             | Notes                                                                                |
| -------------------------------------------------------------------- | ---------------------- | ------------------------------------------------------------------------------------ |
| `SHAGenerator` in [sha_generator.py](sha_generator.py)               | `ocr.ShaGenerator`     | `MessageDigest.getInstance("SHA-256")`                                               |
| `AutoOCR` in [ocr_auto.py](ocr_auto.py)                              | `ocr.TesseractOcr`     | Tess4J with EN+JA trained data                                                       |
| `imagehash.average_hash()` calls throughout                          | `ocr.PerceptualHasher` | Custom port: grayscale, resize 8x8 (or NxN), mean threshold, bit array to hex string |
| Digit template matching in [gen_summary.py](gen_summary.py) L155-213 | `ocr.ScoreDetector`    | Load template hashes, compare via Hamming distance                                   |


### 6.6 UI (`com.sdvxhelper.ui`)

All 5 GUIs migrate from PySimpleGUI to JavaFX with FXML:


| Python Source                                      | Controller                                                                                                   | FXML                                                                                  | Notes                                                                          |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| [sdvx_helper.pyw](sdvx_helper.pyw) `SDVXHelper`    | `MainController`, `SettingsController`, `ObsControlController`, `WebhookController`, `GoogleDriveController` | `main.fxml`, `settings.fxml`, `obs_control.fxml`, `webhook.fxml`, `google_drive.fxml` | Currently one ~1800-line class; split into 5 controllers with scene navigation |
| [manage_score.py](manage_score.py) `ScoreViewer`   | `ScoreViewerController`                                                                                      | `score_viewer.fxml`                                                                   | TableView for score data                                                       |
| [play_log_sync.py](play_log_sync.py) `PlayLogSync` | `PlayLogSyncController`                                                                                      | `play_log_sync.fxml`                                                                  |                                                                                |
| [ocr_reporter.py](ocr_reporter.py) `Reporter`      | `OcrReporterController`                                                                                      | `ocr_reporter.fxml`                                                                   |                                                                                |
| [update.py](update.py) `Updater`                   | `UpdaterController`                                                                                          | `updater.fxml`                                                                        |                                                                                |


### 6.7 Config / i18n / Util


| Python Source                                              | Java Class                                                                                             |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| [manage_settings.py](manage_settings.py) `default_val`     | `config.DefaultSettings`                                                                               |
| [params_secret.py](params_secret.py)                       | `config.SecretConfig` (loaded from env vars or `secrets.properties`)                                   |
| [poor_man_resource_bundle.py](poor_man_resource_bundle.py) | `i18n.MessageService` (wraps `java.util.ResourceBundle` -- the JDK already does what this class does)  |
| [sdvx_utils.py](sdvx_utils.py)                             | `util.VersionUtil`, `util.ScoreFormatter`                                                              |
| [special_titles.py](special_titles.py)                     | `util.SpecialTitles` initially, then externalized to `special_titles.json` in Phase 7 (see Section 13) |


---

## 7. Maven Project Setup (Multi-Module)

### 7.1 Parent POM (`pom.xml` at project root)

The parent POM defines shared properties, dependency management (versions), plugin management, and lists all 6 modules.

```xml
<project>
    <groupId>com.sdvxhelper</groupId>
    <artifactId>sdvx-helper-parent</artifactId>
    <version>2.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>sdvx-helper-core</module>
        <module>sdvx-helper-app</module>
        <module>score-viewer-app</module>
        <module>ocr-reporter-app</module>
        <module>play-log-sync-app</module>
        <module>updater-app</module>
    </modules>

    <properties>
        <java.version>25</java.version>
        <javafx.version>25</javafx.version>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Internal -->
            <dependency><groupId>com.sdvxhelper</groupId><artifactId>sdvx-helper-core</artifactId><version>${project.version}</version></dependency>
            <!-- JavaFX -->
            <dependency><groupId>org.openjfx</groupId><artifactId>javafx-controls</artifactId><version>${javafx.version}</version></dependency>
            <dependency><groupId>org.openjfx</groupId><artifactId>javafx-fxml</artifactId><version>${javafx.version}</version></dependency>
            <!-- JAXB -->
            <dependency><groupId>jakarta.xml.bind</groupId><artifactId>jakarta.xml.bind-api</artifactId></dependency>
            <dependency><groupId>org.glassfish.jaxb</groupId><artifactId>jaxb-runtime</artifactId></dependency>
            <!-- JSON-B API + JSON-P API -->
            <dependency><groupId>jakarta.json.bind</groupId><artifactId>jakarta.json.bind-api</artifactId></dependency>
            <dependency><groupId>jakarta.json</groupId><artifactId>jakarta.json-api</artifactId></dependency>
            <!-- Eclipse Yasson (JSON-B impl) + Parsson (JSON-P impl) -->
            <dependency><groupId>org.eclipse</groupId><artifactId>yasson</artifactId></dependency>
            <dependency><groupId>org.eclipse.parsson</groupId><artifactId>parsson</artifactId></dependency>
            <!-- Jsoup -->
            <dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId></dependency>
            <!-- Tess4J -->
            <dependency><groupId>net.sourceforge.tess4j</groupId><artifactId>tess4j</artifactId></dependency>
            <!-- JNativeHook -->
            <dependency><groupId>com.github.kwhat</groupId><artifactId>jnativehook</artifactId></dependency>
            <!-- SLF4J + Log4J2 -->
            <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId></dependency>
            <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId></dependency>
            <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j2-impl</artifactId></dependency>
            <!-- Testing -->
            <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
            <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><scope>test</scope></dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 7.2 Core Module POM (`sdvx-helper-core/pom.xml`)

Declares all shared dependencies (JavaFX, JAXB, JSON-B, Jsoup, Tess4J, JNativeHook, Log4J2). Packaging is `jar` (library). Tests live here.

### 7.3 Application Module POMs (one per app)

Each app module POM follows the same pattern:

```xml
<project>
    <parent>
        <groupId>com.sdvxhelper</groupId>
        <artifactId>sdvx-helper-parent</artifactId>
        <version>2.0.0</version>
    </parent>
    <artifactId>sdvx-helper-app</artifactId> <!-- varies per module -->
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.sdvxhelper</groupId>
            <artifactId>sdvx-helper-core</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Maven Shade: produces uber JAR with human-friendly name -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <configuration>
                    <finalName>sdvx_helper</finalName> <!-- human-friendly name, varies per module -->
                    <transformers>
                        <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                            <mainClass>com.sdvxhelper.app.SdvxHelperApp</mainClass> <!-- varies per module -->
                        </transformer>
                    </transformers>
                </configuration>
            </plugin>
            <!-- JPackage: produces native .exe with human-friendly name -->
            <plugin>
                <groupId>org.panteleyev</groupId>
                <artifactId>jpackage-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.sdvxhelper.app.SdvxHelperApp</mainClass> <!-- varies per module -->
                    <name>sdvx_helper</name> <!-- human-friendly name, varies per module -->
                    <type>APP_IMAGE</type>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.4 Resource Layout (in `sdvx-helper-core`)

```
sdvx-helper-core/src/main/resources/
├── com/sdvxhelper/ui/view/     # Shared FXML files
├── images/                      # All reference PNGs (score digits, lamps, etc.)
├── i18n/
│   ├── messages_en.properties   # Existing, use java.util.ResourceBundle
│   └── messages_ja.properties
├── params.json                  # Detection parameters
├── log4j2.xml                   # Log4J2 configuration (rolling file appenders)
└── version.properties           # App version
```

Each app module may also contain its own FXML files under `src/main/resources/com/sdvxhelper/app/view/`.

---

## 8. Unit Test Plan

All tests use **JUnit 5** + **Mockito** for mocking network/IO.


| Test Class                  | Target                         | Key Test Cases                                                                                                |
| --------------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `VolforceCalculatorTest`    | `VolforceCalculator`           | VF for each score rank boundary (9900000, 9800000, ...), each lamp type, edge cases (non-int level returns 0) |
| `OnePlayDataTest`           | `OnePlayData`                  | Equality, ordering by date, diff computation                                                                  |
| `MusicInfoTest`             | `MusicInfo`                    | VF computation, ordering by VF                                                                                |
| `OneLevelStatTest`          | `OneLevelStat`                 | Reset, read, average score calculation                                                                        |
| `StatsTest`                 | `Stats`                        | Aggregate across levels, non-int level skipped                                                                |
| `PerceptualHasherTest`      | `PerceptualHasher`             | Known image -> known hash, Hamming distance comparison, hex round-trip                                        |
| `ScoreDetectorTest`         | `ScoreDetector`                | Digit recognition from template images, full 8-digit score assembly                                           |
| `ShaGeneratorTest`          | `ShaGenerator`                 | SHA-256 from bytes, from BufferedImage, deterministic output                                                  |
| `SpecialTitlesTest`         | `SpecialTitles`                | Known title normalizations, passthrough for unknown titles                                                    |
| `VersionUtilTest`           | `VersionUtil`                  | Version parsing, comparison (equal, less, greater)                                                            |
| `ScoreFormatterTest`        | `ScoreFormatter`               | Format with/without bold, various score lengths                                                               |
| `SettingsRepositoryTest`    | `SettingsRepository`           | Load, save, merge defaults for missing keys                                                                   |
| `PlayLogRepositoryTest`     | `PlayLogRepository`            | XML marshal/unmarshal round-trip, empty log creation                                                          |
| `MusicListRepositoryTest`   | `MusicListRepository`          | XML marshal/unmarshal round-trip, hash index building                                                         |
| `SdvxLoggerServiceTest`     | `SdvxLoggerService`            | Push play, pop illegal logs, update best, VF computation (mock repos)                                         |
| `XmlExportServiceTest`      | `XmlExportService`             | History, battle, VF-on-select XML generation (validate output XML schema)                                     |
| `CsvExportServiceTest`      | `CsvExportService`             | Best CSV, alllog CSV, playcount CSV content verification                                                      |
| `Maya2ClientTest`           | `Maya2Client`                  | Health check, musiclist fetch, upload with HMAC (mock HttpClient)                                             |
| `ObsWebSocketClientTest`    | `ObsWebSocketClient`           | Connect, screenshot retrieval, scene/source control (mock WebSocket)                                          |
| `DiscordPresenceClientTest` | `DiscordPresenceClient`        | Update throttling (5s), difficulty normalization, destroy cleanup                                             |
| `MessageServiceTest`        | `MessageService`               | Load EN/JA, key lookup, placeholder formatting, missing key exception                                         |
| `ImageAnalysisServiceTest`  | `ImageAnalysisService`         | Result detection, difficulty detection from pixel sums, lamp detection                                        |
| `DataMigrationTest`         | Pickle migration script output | Validate migrated XML files against JAXB schema                                                               |


---

## 9. Javadoc Documentation Standards

All Java code must include Javadoc:

- **Every public class**: purpose, author, since version
- **Every public method**: description, `@param` for each parameter, `@return`, `@throws`
- **Every public field/constant**: brief description
- **Package-level**: `package-info.java` in each package describing its role
- **Complex algorithms**: inline comments for non-obvious logic (VF formula coefficients, perceptual hash algorithm, HMAC signing)

---

## 10. Migration Execution Order

The migration should proceed bottom-up (models first, then services, then UI):

**Phase 0 -- Python Relocation**: Move all Python source, resources, and build scripts into `python/`. Update `build.cmd`, `build.sh`, and `.gitignore` so the legacy app still builds cleanly from within `python/`.
**Phase 1 -- Foundation**: Multi-module Maven project (parent + core + 5 app modules), models, enums, config, i18n, util
**Phase 2 -- Data Layer**: Repositories (JAXB XML I/O), settings (JSON-B / Eclipse Yasson), pickle migration script
**Phase 3 -- Core Services**: VolforceCalculator, SdvxLoggerService, ImageAnalysisService, PerceptualHasher, ScoreDetector
**Phase 4 -- Network Layer**: Maya2Client, ObsWebSocketClient, DiscordPresenceClient, WebhookClient, GitHubVersionClient
**Phase 5 -- UI Layer**: JavaFX FXML layouts + controllers for all 5 apps
**Phase 6 -- Build & Package**: Maven Shade uber JARs (5), JPackage native executables (5), distribution zip, build scripts

**Phase 7 -- Post-Migration Optimization**: Externalize `special_titles` to a user-editable JSON file (see Section 13).

Each phase includes writing unit tests and Javadoc as classes are created.

---

## 11. Key Architecture Decisions

- **No god classes**: Python's `SDVXHelper` (~~1800 lines) and `SDVXLogger` (~~900 lines) are decomposed into focused services, controllers, and repositories.
- **Dependency injection pattern**: Services receive repositories/clients via constructor injection (no static singletons), enabling testability with Mockito.
- **FXML separation**: UI layout in FXML files, logic in controller classes -- clean MVC.
- **Thread management**: Use `java.util.concurrent.ExecutorService` and JavaFX `Platform.runLater()` for background tasks instead of raw `threading.Thread`.
- **Secrets handling**: Replace `params_secret.py` with environment variables or a `secrets.properties` file excluded from version control (same `.gitignore` pattern).
- **Image processing**: Stay with JDK built-in `BufferedImage` / `ImageIO` -- no need for a heavy native library since the app only does cropping, resizing, grayscale conversion, and pixel-sum operations.
- **Python preservation**: All original Python code is retained under `python/` with working build scripts, allowing side-by-side operation and reference during the Java migration. The `python/` folder can be removed once the Java port is validated and complete.

---

## 12. Output Artifacts

### 12.1 Build Artifacts (produced by `mvn package` from project root)

The multi-module build produces **5 uber JARs** and **5 native executables**, one per tool -- matching the original Python project's 5 `.exe` files.

**Shared library (not directly executable):**


| Artifact | Path                                                 | Description                                                                                                                                  |
| -------- | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Core JAR | `sdvx-helper-core/target/sdvx-helper-core-2.0.0.jar` | Shared library containing all models, services, repositories, networking, OCR, config, i18n, and utilities. Dependency of all 5 app modules. |


**Uber JARs (one per tool, each self-contained):**

Maven Shade is configured with `<finalName>` to produce clean, human-friendly JAR names -- no version suffixes, no `-shaded` tags.


| Tool          | Uber JAR Path                                | Final Name          | Main Class       | Maps To (Python)    |
| ------------- | -------------------------------------------- | ------------------- | ---------------- | ------------------- |
| SDVX Helper   | `sdvx-helper-app/target/sdvx_helper.jar`     | `sdvx_helper.jar`   | `SdvxHelperApp`  | `sdvx_helper.exe`   |
| Score Viewer  | `score-viewer-app/target/manage_score.jar`   | `manage_score.jar`  | `ScoreViewerApp` | `manage_score.exe`  |
| OCR Reporter  | `ocr-reporter-app/target/ocr_reporter.jar`   | `ocr_reporter.jar`  | `OcrReporterApp` | `ocr_reporter.exe`  |
| Play Log Sync | `play-log-sync-app/target/play_log_sync.jar` | `play_log_sync.jar` | `PlayLogSyncApp` | `play_log_sync.exe` |
| Updater       | `updater-app/target/update.jar`              | `update.jar`        | `UpdaterApp`     | `update.exe`        |


Each uber JAR is runnable via `java -jar sdvx_helper.jar` (no version or suffix to remember) and contains all dependencies (core + JavaFX + JAXB + Yasson + Jsoup + Tess4J + JNativeHook + Log4J2).

**JPackage native executables (one per tool):**

JPackage `<name>` is configured to match the original Python executable names exactly.


| Tool          | App Image Path                                     | Executable          | Maps To (Python)    |
| ------------- | -------------------------------------------------- | ------------------- | ------------------- |
| SDVX Helper   | `sdvx-helper-app/target/jpackage/sdvx_helper/`     | `sdvx_helper.exe`   | `sdvx_helper.exe`   |
| Score Viewer  | `score-viewer-app/target/jpackage/manage_score/`   | `manage_score.exe`  | `manage_score.exe`  |
| OCR Reporter  | `ocr-reporter-app/target/jpackage/ocr_reporter/`   | `ocr_reporter.exe`  | `ocr_reporter.exe`  |
| Play Log Sync | `play-log-sync-app/target/jpackage/play_log_sync/` | `play_log_sync.exe` | `play_log_sync.exe` |
| Updater       | `updater-app/target/jpackage/update/`              | `update.exe`        | `update.exe`        |


Each JPackage app-image bundles the JRE and the uber JAR into a self-contained native application requiring no pre-installed Java runtime. The executable names are identical to the original Python artifacts for a seamless transition.

### 12.2 Runtime Data Files (produced/consumed at runtime)


| File                   | Format        | Description                                                                                                        |
| ---------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------ |
| `alllog.xml`           | XML (JAXB)    | Play history log -- list of all `OnePlayData` entries. Replaces `alllog.pkl`.                                      |
| `musiclist.xml`        | XML (JAXB)    | Song database -- jacket hashes, info hashes, SHA hashes, title metadata, grade-S tables. Replaces `musiclist.pkl`. |
| `rival_log.xml`        | XML (JAXB)    | Rival score data -- per-rival list of `MusicInfo`. Replaces `out/rival_log.pkl`.                                   |
| `title_conv_table.xml` | XML (JAXB)    | Title mapping table for Maya2 name normalization. Replaces `resources/title_conv_table.pkl`.                       |
| `settings.json`        | JSON (JSON-B) | User settings -- OBS config, webhooks, Google Drive, Discord presence, UI preferences.                             |
| `special_titles.json`  | JSON (JSON-B) | Externalized title normalization maps -- editable without recompilation (Phase 7). Falls back to bundled defaults. |


### 12.3 OBS Overlay XML Files (produced at runtime, consumed by OBS browser sources)


| File                                        | Description                                             |
| ------------------------------------------- | ------------------------------------------------------- |
| `out/history_cursong.xml`                   | Current song play history (score, lamp, date per play)  |
| `out/sdvx_battle.xml`                       | Today's plays for battle overlay                        |
| `out/vf_onselect.xml`                       | Volforce info for the currently selected song           |
| `out/rival.xml`                             | Rival comparison rankings for the current song          |
| `out/total_vf.xml` / `out/rta_total_vf.xml` | Overall Volforce with top-50 breakdown                  |
| `out/stats.xml` / `out/rta_stats.xml`       | Per-level statistics (rank/lamp distribution, averages) |
| `out/rival_updates.xml`                     | Rival score update notifications                        |


### 12.4 CSV Exports (produced on user action)


| File                    | Description                                                                   |
| ----------------------- | ----------------------------------------------------------------------------- |
| `out/best_*.csv`        | Best scores per chart (title, difficulty, Lv, score, lamp, VF) -- UTF-8       |
| `out/alllog_*.csv`      | Full play history (title, difficulty, Lv, score, lamp, VF, date) -- Shift-JIS |
| `out/playcount_*.csv`   | Play count per day                                                            |
| `out/maya2_payload.csv` | Upload payload for Maya2 server (with HMAC checksum footer)                   |


### 12.5 Image Outputs (produced at runtime)


| File                    | Description                                                                              |
| ----------------------- | ---------------------------------------------------------------------------------------- |
| `out/summary_full.png`  | Full-size session summary image (all results stacked)                                    |
| `out/summary_small.png` | Compact session summary image                                                            |
| `out/select_jacket.png` | Cropped jacket image from song select screen                                             |
| `out/select_*.png`      | Cropped UI parts from song select (title, level, difficulty, BPM, effector, illustrator) |
| `out/part_*.png`        | Cropped result screen parts (rank, lamp, score, jacket, etc.)                            |
| `jackets/{hash}.png`    | Jacket images indexed by perceptual hash (for VF view)                                   |


### 12.6 Log Files (produced at runtime)


| File                    | Description                                         |
| ----------------------- | --------------------------------------------------- |
| `log/sdvx_helper.log`   | Main application log (rotating, 2 MB max, 1 backup) |
| `log/ocr.log`           | OCR/image analysis log                              |
| `log/presense.log`      | Discord presence log                                |
| `log/auto_ocr.log`      | Tess4J OCR log                                      |
| `log/sdvxh_classes.log` | Domain logic / logger service log                   |
| `log/obssocket.log`     | OBS WebSocket communication log                     |


All log files use SLF4J + Log4J2 with `RollingFileAppender` (2 MB max size, 1 backup), configured via `log4j2.xml`.

### 12.7 Documentation Artifacts (produced by `mvn javadoc:javadoc`)


| Artifact     | Path                   | Description                                                     |
| ------------ | ---------------------- | --------------------------------------------------------------- |
| Javadoc site | `target/site/apidocs/` | Full HTML Javadoc for all public classes, methods, and packages |


---

## 13. Post-Migration Optimization: Externalize Special Titles (Phase 7)

### 13.1 Problem

In the Python codebase, [special_titles.py](special_titles.py) hardcodes four data structures as Python dicts:

- `special_titles` -- Maps filesystem-safe title strings to their original titles (containing characters like `?`, `*`, `"`, `/`, `:` that Windows forbids in filenames)
- `direct_overides` -- Manual level rating overrides for songs not in the main song list
- `ignored_names` -- Titles to skip during processing
- `direct_removes` -- Titles to remove/ignore

These are used by `sdvx_utils.restore_title()` and `find_song_rating()` for title normalization across the application. When new songs are added to the game, this file must be edited, the code recompiled, and a new release distributed -- just to add a simple string mapping.

### 13.2 Solution

Externalize all four maps into a single `special_titles.json` file that can be edited by users without recompilation.

**File location:** `resources/special_titles.json` (next to `settings.json`, shipped with the app but user-editable)

**JSON structure:**

```json
{
  "specialTitles": {
    "Death by Glamour  華麗なる死闘": "Death by Glamour / 華麗なる死闘",
    "archivezip": "archive::zip"
  },
  "directOverrides": {
    "SomeTitle": ["APPEND:20", "EXH:18"]
  },
  "ignoredNames": ["ignored1", "ignored2"],
  "directRemoves": ["remove1", "remove2"]
}
```

### 13.3 Implementation

- `**util.SpecialTitles**` becomes a POJO with JSON-B annotations, loaded by a new `**repository.SpecialTitlesRepository**`
- At startup, the repository loads `special_titles.json` from the working directory. If the file does not exist, it falls back to a bundled default embedded in `sdvx-helper-core/src/main/resources/special_titles.json`
- The `TitleNormalizer` service (which wraps the lookup logic from `sdvx_utils.restore_title()`) receives `SpecialTitlesRepository` via constructor injection
- A unit test (`SpecialTitlesRepositoryTest`) validates loading from file, fallback to bundled defaults, and merge behavior

### 13.4 Benefit

New song title mappings, rating overrides, or ignore rules can be added by editing a single JSON file -- no rebuild, no new release required. The bundled defaults still ensure the app works out of the box.