package com.sdvxhelper.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LampFormatter}.
 */
class LampFormatterTest {

    // -------------------------------------------------------------------------
    // Special mappings
    // -------------------------------------------------------------------------

    @Test
    void exhLowercaseDisplaysAsMaxxive() {
        Assertions.assertEquals("MAXXIVE", LampFormatter.formatDisplay("exh"));
    }

    @Test
    void exhUppercaseDisplaysAsMaxxive() {
        Assertions.assertEquals("MAXXIVE", LampFormatter.formatDisplay("EXH"));
    }

    @Test
    void exhMixedCaseDisplaysAsMaxxive() {
        Assertions.assertEquals("MAXXIVE", LampFormatter.formatDisplay("Exh"));
    }

    @Test
    void classClearLowercaseDisplaysAsSkillClear() {
        Assertions.assertEquals("SKILL CLEAR", LampFormatter.formatDisplay("class_clear"));
    }

    @Test
    void classClearUppercaseDisplaysAsSkillClear() {
        Assertions.assertEquals("SKILL CLEAR", LampFormatter.formatDisplay("CLASS_CLEAR"));
    }

    @Test
    void classClearMixedCaseDisplaysAsSkillClear() {
        Assertions.assertEquals("SKILL CLEAR", LampFormatter.formatDisplay("Class_Clear"));
    }

    // -------------------------------------------------------------------------
    // All other known lamp values — uppercased passthrough
    // -------------------------------------------------------------------------

    @Test
    void pucDisplaysAsUppercase() {
        Assertions.assertEquals("PUC", LampFormatter.formatDisplay("puc"));
    }

    @Test
    void ucDisplaysAsUppercase() {
        Assertions.assertEquals("UC", LampFormatter.formatDisplay("uc"));
    }

    @Test
    void hardDisplaysAsUppercase() {
        Assertions.assertEquals("HARD", LampFormatter.formatDisplay("hard"));
    }

    @Test
    void clearDisplaysAsUppercase() {
        Assertions.assertEquals("CLEAR", LampFormatter.formatDisplay("clear"));
    }

    @Test
    void failedDisplaysAsUppercase() {
        Assertions.assertEquals("FAILED", LampFormatter.formatDisplay("failed"));
    }

    @Test
    void appendDisplaysAsUppercase() {
        Assertions.assertEquals("APPEND", LampFormatter.formatDisplay("append"));
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void nullReturnsEmptyString() {
        Assertions.assertEquals("", LampFormatter.formatDisplay(null));
    }

    @Test
    void unknownValueIsUppercased() {
        Assertions.assertEquals("UNKNOWNLAMP", LampFormatter.formatDisplay("unknownlamp"));
    }

    @Test
    void alreadyUppercasePassthroughIsIdempotent() {
        Assertions.assertEquals("HARD", LampFormatter.formatDisplay("HARD"));
    }
}
