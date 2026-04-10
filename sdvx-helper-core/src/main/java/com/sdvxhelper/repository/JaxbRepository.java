package com.sdvxhelper.repository;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Generic base class providing JAXB marshal/unmarshal operations for any POJO root element.
 *
 * <p>All file-backed XML repositories extend this class, supplying the root type.
 * Writes are performed atomically by writing to a {@code .tmp} file first, then
 * renaming it to the target path, reducing the risk of data corruption on
 * application crash.</p>
 *
 * @param <T> the JAXB-annotated root element type
 * @author sdvx-helper
 * @since 2.0.0
 */
public abstract class JaxbRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(JaxbRepository.class);

    private final JAXBContext context;
    private final Class<T> rootType;

    /**
     * Constructs a repository for the given root type.
     *
     * @param rootType     the JAXB root-element class
     * @param boundClasses additional classes needed by the JAXB context (if any)
     * @throws IllegalStateException if JAXB context initialisation fails
     */
    protected JaxbRepository(Class<T> rootType, Class<?>... boundClasses) {
        this.rootType = rootType;
        try {
            Class<?>[] all = new Class<?>[boundClasses.length + 1];
            all[0] = rootType;
            System.arraycopy(boundClasses, 0, all, 1, boundClasses.length);
            this.context = JAXBContext.newInstance(all);
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to initialise JAXB context for " + rootType.getSimpleName(), e);
        }
    }

    /**
     * Unmarshals an XML file into an instance of {@code T}.
     *
     * @param file source XML file
     * @return unmarshalled object
     * @throws JAXBException if unmarshalling fails
     */
    protected T load(File file) throws JAXBException {
        log.debug("Loading {} from {}", rootType.getSimpleName(), file.getAbsolutePath());
        Unmarshaller u = context.createUnmarshaller();
        @SuppressWarnings("unchecked")
        T result = (T) u.unmarshal(file);
        return result;
    }

    /**
     * Marshals {@code data} to an XML file using a write-then-rename strategy.
     *
     * @param data   object to marshal
     * @param target destination file
     * @throws JAXBException if marshalling fails
     * @throws IOException   if the temporary file cannot be renamed
     */
    protected void save(T data, File target) throws JAXBException, IOException {
        log.debug("Saving {} to {}", rootType.getSimpleName(), target.getAbsolutePath());
        // Ensure parent directory exists
        Path parentDir = target.toPath().getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
        Marshaller m = context.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        m.marshal(data, tmp.toFile());
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.debug("Saved {} successfully", rootType.getSimpleName());
    }
}
