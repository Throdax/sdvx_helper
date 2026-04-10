package com.sdvxhelper.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link SpecialTitles}.
 */
class SpecialTitlesTest {

    private SpecialTitles st;

    @BeforeEach
    void setUp() {
        st = new SpecialTitles();
        st.setSpecialTitles(Map.of(
            "archivezip", "archive::zip",
            "LuckyClover", "Lucky*Clover"
        ));
        st.setDirectOverrides(Map.of(
            "Pure Evil", List.of("NOV:5", "ADV:12", "EXH:17")
        ));
        st.setIgnoredNames(List.of("Help me, CODYYYYYY!!"));
        st.setDirectRemoves(List.of("Some Bad Title"));
    }

    @Test
    void restoreTitleReturnsMapping() {
        Assertions.assertEquals("archive::zip", st.restoreTitle("archivezip"));
        Assertions.assertEquals("Lucky*Clover", st.restoreTitle("LuckyClover"));
    }

    @Test
    void restoreTitlePassesThroughUnknown() {
        Assertions.assertEquals("Unknown Song", st.restoreTitle("Unknown Song"));
    }

    @Test
    void isIgnoredReturnsTrueForKnown() {
        Assertions.assertTrue(st.isIgnored("Help me, CODYYYYYY!!"));
    }

    @Test
    void isIgnoredReturnsFalseForUnknown() {
        Assertions.assertFalse(st.isIgnored("冥"));
    }

    @Test
    void shouldRemoveReturnsTrueForListed() {
        Assertions.assertTrue(st.shouldRemove("Some Bad Title"));
    }

    @Test
    void shouldRemoveReturnsFalseForUnlisted() {
        Assertions.assertFalse(st.shouldRemove("冥"));
    }

    @Test
    void getDirectOverridesReturnsCorrectList() {
        List<String> overrides = st.getDirectOverrides("Pure Evil");
        Assertions.assertEquals(3, overrides.size());
        Assertions.assertTrue(overrides.contains("EXH:17"));
    }

    @Test
    void getDirectOverridesReturnsEmptyListForUnknown() {
        Assertions.assertTrue(st.getDirectOverrides("Unknown Song").isEmpty());
    }

    @Test
    void setNullSpecialTitlesDefaultsToEmptyMap() {
        st.setSpecialTitles(null);
        Assertions.assertEquals("A", st.restoreTitle("A"));
    }

    @Test
    void setNullIgnoredNamesDefaultsToEmptyList() {
        st.setIgnoredNames(null);
        Assertions.assertFalse(st.isIgnored("Help me, CODYYYYYY!!"));
    }
}
