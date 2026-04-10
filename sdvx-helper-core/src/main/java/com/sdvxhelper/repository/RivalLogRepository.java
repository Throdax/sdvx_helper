package com.sdvxhelper.repository;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.RivalEntry;
import com.sdvxhelper.model.RivalLog;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Persists and loads rival score data as {@code rival_log.xml}.
 *
 * <p>Replaces the Python {@code pickle.load/dump} calls on {@code rival_log.pkl}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class RivalLogRepository extends JaxbRepository<RivalLog> {

    private static final Logger log = LoggerFactory.getLogger(RivalLogRepository.class);
    private static final String DEFAULT_PATH = "out/rival_log.xml";

    private final File file;

    /**
     * Constructs a repository backed by the default file.
     */
    public RivalLogRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file.
     *
     * @param file XML file to read from / write to
     */
    public RivalLogRepository(File file) {
        super(RivalLog.class, RivalEntry.class, MusicInfo.class);
        this.file = file;
    }

    /**
     * Loads the rival log from disk.
     * Returns an empty {@link RivalLog} if the file does not exist.
     *
     * @return loaded rival log
     */
    public RivalLog load() {
        if (!file.exists()) {
            log.info("rival_log.xml not found; returning empty log");
            return new RivalLog();
        }
        try {
            RivalLog rl = super.load(file);
            log.info("Loaded rival_log.xml ({} rivals)", rl.getRivals().size());
            return rl;
        } catch (JAXBException e) {
            log.error("Failed to load rival_log.xml; returning empty log", e);
            return new RivalLog();
        }
    }

    /**
     * Saves the rival log to disk atomically.
     *
     * @param rivalLog rival log to persist
     * @throws IOException if the file cannot be written
     */
    public void save(RivalLog rivalLog) throws IOException {
        try {
            super.save(rivalLog, file);
        } catch (JAXBException e) {
            throw new IOException("Failed to marshal rival log to XML", e);
        }
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
