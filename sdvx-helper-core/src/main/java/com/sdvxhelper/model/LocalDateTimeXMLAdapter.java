package com.sdvxhelper.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class LocalDateTimeXMLAdapter extends XmlAdapter<String, LocalDateTime> {

    private static final DateTimeFormatter JAVA_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter PYTHON_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public LocalDateTime unmarshal(String v) throws Exception {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(v, JAVA_FORMAT);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v, PYTHON_FORMAT);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v, ISO_FORMAT);
        } catch (DateTimeParseException ignored) {
        }
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
