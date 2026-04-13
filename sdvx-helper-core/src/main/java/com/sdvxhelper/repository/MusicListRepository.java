package com.sdvxhelper.repository;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jakarta.xml.bind.JAXBException;

import com.sdvxhelper.model.DifficultyHashGroup;
import com.sdvxhelper.model.DifficultyHashes;
import com.sdvxhelper.model.GradeSEntry;
import com.sdvxhelper.model.HashEntry;
import com.sdvxhelper.model.MusicList;
import com.sdvxhelper.model.SongInfo;
import com.sdvxhelper.model.SongInfoEntry;
import com.sdvxhelper.model.TierEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists and loads the song/jacket database as {@code musiclist.xml}.
 *
 * <p>
 * Replaces the Python {@code pickle.load/dump} calls on {@code musiclist.pkl}.
 * Also builds in-memory index maps for O(1) hash lookups during the detection
 * loop.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class MusicListRepository extends JaxbRepository<MusicList> {

    private static final Logger log = LoggerFactory.getLogger(MusicListRepository.class);
    private static final String DEFAULT_PATH = "musiclist.xml";

    private final File file;

    /** In-memory index: perceptual hash → (difficulty, title). */
    private Map<String, String[]> jacketHashIndex = new HashMap<>();

    /** In-memory index: song title → {@link SongInfo}. */
    private Map<String, SongInfo> titleIndex = new HashMap<>();

    /**
     * Constructs a repository backed by the default file.
     */
    public MusicListRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file.
     *
     * @param file
     *            XML file to read from / write to
     */
    public MusicListRepository(File file) {
        super(MusicList.class, DifficultyHashGroup.class, DifficultyHashes.class, HashEntry.class, SongInfoEntry.class,
                SongInfo.class, GradeSEntry.class, TierEntry.class);
        this.file = file;
    }

    /**
     * Loads the music list from disk and rebuilds in-memory indices.
     *
     * <p>
     * Returns {@code null} if the file does not exist; the caller should trigger a
     * download in that case.
     * </p>
     *
     * @return loaded {@link MusicList}, or {@code null} if the file is absent
     */
    public MusicList load() {
        if (!file.exists()) {
            log.info("musiclist.xml not found at {}", file.getAbsolutePath());
            return null;
        }
        try {
            MusicList musiclist = super.load(file);
            buildIndices(musiclist);
            log.info("Loaded musiclist.xml ({} songs)", musiclist.getTitles().size());
            return musiclist;
        } catch (JAXBException e) {
            log.error("Failed to load musiclist.xml", e);
            return null;
        }
    }

    /**
     * Saves the music list to disk atomically.
     *
     * @param musicList
     *            music list to persist
     * @throws IOException
     *             if the file cannot be written
     */
    public void save(MusicList musicList) throws IOException {
        try {
            super.save(musicList, file);
            buildIndices(musicList);
        } catch (JAXBException e) {
            throw new IOException("Failed to marshal music list to XML", e);
        }
    }

    /**
     * Looks up the song title and difficulty for a given jacket perceptual hash.
     *
     * @param hash
     *            perceptual hash hex string
     * @return {@code String[]{title, difficulty}} or {@code null} if not found
     */
    public String[] findByJacketHash(String hash) {
        return jacketHashIndex.get(hash);
    }

    /**
     * Returns the {@link SongInfo} for a given title, or {@code null} if unknown.
     *
     * @param title
     *            song title
     * @return song metadata or {@code null}
     */
    public SongInfo findSongInfo(String title) {
        return titleIndex.get(title);
    }

    /**
     * Returns an unmodifiable view of the title → SongInfo index.
     *
     * @return title index map
     */
    public Map<String, SongInfo> getTitleIndex() {
        return java.util.Collections.unmodifiableMap(titleIndex);
    }

    /**
     * Builds in-memory indices for O(1) lookups by jacket hash and title.
     *
     * @param musicList
     *            the music list to index
     */
    private void buildIndices(MusicList musicList) {
        jacketHashIndex = new HashMap<>();
        titleIndex = new HashMap<>();

        // Build jacket hash index
        for (DifficultyHashGroup group : musicList.getJacket()) {
            String diff = group.getDifficulty();
            for (HashEntry entry : group.getHashes().getEntries()) {
                jacketHashIndex.put(entry.getHash(), new String[]{entry.getTitle(), diff});
            }
        }

        // Build title index
        for (SongInfoEntry sie : musicList.getTitles()) {
            titleIndex.put(sie.getTitle(), sie.getSongInfo());
        }

        log.debug("Built indices: {} jacket hashes, {} titles", jacketHashIndex.size(), titleIndex.size());
    }

    /**
     * Returns the backing file.
     *
     * @return backing XML file
     */
    public File getFile() {
        return file;
    }
}
