/**
 * JAXB-annotated model classes for the SDVX Helper play log and music list.
 *
 * <p>
 * The package-level {@code @XmlJavaTypeAdapters} registers
 * {@link com.sdvxhelper.model.LocalDateTimeXMLAdapter} for every
 * {@link java.time.LocalDateTime} field in this package, ensuring JAXB
 * marshals and unmarshals date-time values correctly regardless of whether the
 * field carries its own {@code @XmlJavaTypeAdapter} annotation.
 * Field-level annotations alone are not reliably honoured by all Jakarta JAXB 3
 * implementations.
 * </p>
 */
@XmlJavaTypeAdapters({
    @XmlJavaTypeAdapter(type = LocalDateTime.class, value = LocalDateTimeXMLAdapter.class)
})
package com.sdvxhelper.model;

import java.time.LocalDateTime;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
