package com.sdvxhelper.repository;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Persists and loads the complete play history as {@code alllog.xml}.
 *
 * <p>Replaces the Python {@code pickle.load/dump} calls on {@code alllog.pkl}.
 * On first use (when no file exists), an empty {@link PlayLog} is returned and
 * written to disk on the next {@link #save(PlayLog)} call.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PlayLogRepository extends JaxbRepository<PlayLog> {

    private static final Logger log = LoggerFactory.getLogger(PlayLogRepository.class);
    private static final String DEFAULT_PATH = "alllog.xml";

    private final File file;

    /**
     * Constructs a repository backed by the default file ({@code alllog.xml} in the
     * current working directory).
     */
    public PlayLogRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file path.
     * Useful for testing.
     *
     * @param file XML file to read from / write to
     */
    public PlayLogRepository(File file) {
        super(PlayLog.class, OnePlayData.class);
        this.file = file;
    }

    /**
     * Loads the play history from disk.
     *
     * <p>If the file does not yet exist, returns a new empty {@link PlayLog}.
     * The list is sorted by date ascending after loading.</p>
     *
     * @return loaded (or newly created) play log
     */
    public PlayLog load() {
        if (!file.exists()) {
            log.info("alllog.xml not found at {}, starting with empty log", file.getAbsolutePath());
            return new PlayLog();
        }
        try {
            PlayLog pl = super.load(file);
            if (pl.getPlays() == null) {
                pl.setPlays(new ArrayList<>());
            }
            Collections.sort(pl.getPlays());
            log.info("Loaded {} play records from {}", pl.getPlays().size(), file.getAbsolutePath());
            return pl;
        } catch (JAXBException e) {
            log.error("Failed to load alllog.xml; returning empty log", e);
            return new PlayLog();
        }
    }

    /**
     * Saves the play log to disk atomically.
     *
     * @param playLog play log to persist
     * @throws IOException if the file cannot be written
     */
    public void save(PlayLog playLog) throws IOException {
        try {
            super.save(playLog, file);
        } catch (JAXBException e) {
            throw new IOException("Failed to marshal play log to XML", e);
        }
    }

    /**
     * Returns the backing file path.
     *
     * @return backing XML file
     */
    public File getFile() {
        return file;
    }
}
