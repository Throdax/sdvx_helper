package com.sdvxhelper.app.controller.detection;

import com.sdvxhelper.model.OnePlayData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SelectScreenResult}.
 */
class SelectScreenResultTest {

    // -------------------------------------------------------------------------
    // Positive cases
    // -------------------------------------------------------------------------

    @Test
    void constructorSetsAllFields() {
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        SelectScreenResult result = new SelectScreenResult("My Song", "exh", play);

        Assertions.assertEquals("My Song", result.getTitle());
        Assertions.assertEquals("exh", result.getDiff());
        Assertions.assertSame(play, result.getImportedPlay());
    }

    @Test
    void constructorAcceptsNullImportedPlay() {
        SelectScreenResult result = new SelectScreenResult("My Song", "adv", null);

        Assertions.assertEquals("My Song", result.getTitle());
        Assertions.assertEquals("adv", result.getDiff());
        Assertions.assertNull(result.getImportedPlay());
    }

    @Test
    void getTitleReturnsCorrectValue() {
        SelectScreenResult result = new SelectScreenResult("TITLE_X", "exh", null);
        Assertions.assertEquals("TITLE_X", result.getTitle());
    }

    @Test
    void getDiffReturnsCorrectValue() {
        SelectScreenResult result = new SelectScreenResult("Song", "nov", null);
        Assertions.assertEquals("nov", result.getDiff());
    }

    @Test
    void getImportedPlayReturnsProvidedInstance() {
        OnePlayData play = new OnePlayData("S", 1, 0, "failed", "nov", "");
        SelectScreenResult result = new SelectScreenResult("S", "nov", play);
        Assertions.assertSame(play, result.getImportedPlay());
    }

    // -------------------------------------------------------------------------
    // Negative / edge cases
    // -------------------------------------------------------------------------

    @Test
    void constructorAcceptsNullTitle() {
        SelectScreenResult result = new SelectScreenResult(null, "exh", null);
        Assertions.assertNull(result.getTitle());
    }

    @Test
    void constructorAcceptsNullDiff() {
        SelectScreenResult result = new SelectScreenResult("Song", null, null);
        Assertions.assertNull(result.getDiff());
    }

    @Test
    void constructorAcceptsEmptyStrings() {
        SelectScreenResult result = new SelectScreenResult("", "", null);
        Assertions.assertEquals("", result.getTitle());
        Assertions.assertEquals("", result.getDiff());
    }
}
