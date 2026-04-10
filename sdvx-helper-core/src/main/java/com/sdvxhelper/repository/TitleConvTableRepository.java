package com.sdvxhelper.repository;

import com.sdvxhelper.model.TitleConvTable;
import com.sdvxhelper.model.TitleMapping;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Persists and loads the Maya2 title-conversion table as {@code title_conv_table.xml}.
 *
 * <p>Replaces the Python {@code pickle.load} call on
 * {@code resources/title_conv_table.pkl}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class TitleConvTableRepository extends JaxbRepository<TitleConvTable> {

    private static final Logger log = LoggerFactory.getLogger(TitleConvTableRepository.class);
    private static final String DEFAULT_PATH = "resources/title_conv_table.xml";

    private final File file;

    /**
     * Constructs a repository backed by the default file.
     */
    public TitleConvTableRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file.
     *
     * @param file XML file to read from / write to
     */
    public TitleConvTableRepository(File file) {
        super(TitleConvTable.class, TitleMapping.class);
        this.file = file;
    }

    /**
     * Loads the title-conversion table from disk.
     * Returns an empty {@link TitleConvTable} if the file does not exist.
     *
     * @return loaded table
     */
    public TitleConvTable load() {
        if (!file.exists()) {
            log.info("title_conv_table.xml not found; returning empty table");
            return new TitleConvTable();
        }
        try {
            TitleConvTable table = super.load(file);
            log.info("Loaded title_conv_table.xml ({} entries)", table.getEntries().size());
            return table;
        } catch (JAXBException e) {
            log.error("Failed to load title_conv_table.xml; returning empty table", e);
            return new TitleConvTable();
        }
    }

    /**
     * Loads the table and returns it as a flat {@link Map} for O(1) lookups.
     *
     * @return local title → Maya2 title map
     */
    public Map<String, String> loadAsMap() {
        TitleConvTable table = load();
        return Collections.unmodifiableMap(table.toMap());
    }

    /**
     * Saves the table to disk atomically.
     *
     * @param table table to persist
     * @throws IOException if the file cannot be written
     */
    public void save(TitleConvTable table) throws IOException {
        try {
            super.save(table, file);
        } catch (JAXBException e) {
            throw new IOException("Failed to marshal title conv table to XML", e);
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
