package com.sdvxhelper.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDateTimeXMLAdapter extends XmlAdapter<String, LocalDateTime> {

    private static final Logger log = LoggerFactory.getLogger(LocalDateTimeXMLAdapter.class);

    private static final DateTimeFormatter JAVA_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter PYTHON_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public LocalDateTime unmarshal(String v) throws Exception {
        log.debug("Going to unmarhshal date string: '{}'", v);

        if (v == null || v.isBlank()) {
            log.debug("date string is null or blank - returning null");
            return null;
        }
        try {
            return LocalDateTime.parse(v, JAVA_FORMAT);
        } catch (DateTimeParseException ignored) {
            log.debug("date string '{}' did not match Java format: {}", v, ignored.getMessage());
        }
        try {
            return LocalDateTime.parse(v, PYTHON_FORMAT);
        } catch (DateTimeParseException ignored) {
            log.debug("date string '{}' did not match Python format: {}", v, ignored.getMessage());
        }
        try {
            return LocalDateTime.parse(v, ISO_FORMAT);
        } catch (DateTimeParseException ignored) {
            log.debug("date string '{}' did not match ISO format: {}", v, ignored.getMessage());
        }
        log.warn("LocalDateTimeXMLAdapter: unrecognised date format '{}' - returning null", v);
        return null;
    }

    @Override
    public String marshal(LocalDateTime v) throws Exception {
        if (v == null) {
            return null;
        }
        return v.format(JAVA_FORMAT);
    }

}
