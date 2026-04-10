package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds aggregated statistics for all chart levels (1–20).
 *
 * <p>Contains one {@link OneLevelStat} per level.  After loading the personal-best
 * list from the repository, call {@link #resetAll()} and then {@link #readAll(MusicInfo)}
 * for each chart to rebuild the statistics from scratch.
 *
 * <p>Maps to the Python {@code Stats} class in {@code sdvxh_classes.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class Stats {

    /** One stat entry per level, indices 0–19 corresponding to levels 1–20. */
    private final List<OneLevelStat> data;

    /**
     * Constructs a new {@code Stats} instance and initialises a {@link OneLevelStat}
     * for each level from 1 to 20.
     */
    public Stats() {
        data = new ArrayList<>(20);
        for (int i = 1; i <= 20; i++) {
            data.add(new OneLevelStat(i));
        }
    }

    /**
     * Resets all per-level statistics.  Call this before recomputing from scratch.
     */
    public void resetAll() {
        for (OneLevelStat s : data) {
            s.reset();
        }
    }

    /**
     * Reads one chart's data into the appropriate level bucket.
     * Non-integer levels (e.g. {@code "??"}) are silently ignored.
     *
     * @param minfo chart data to incorporate
     */
    public void readAll(MusicInfo minfo) {
        int lvInt = minfo.getLvAsInt();
        if (lvInt >= 1 && lvInt <= 20) {
            data.get(lvInt - 1).read(minfo);
        }
    }

    /**
     * Returns the {@link OneLevelStat} for the given level.
     *
     * @param level chart level (1–20)
     * @return the stat entry for that level
     * @throws IndexOutOfBoundsException if level is not in range [1, 20]
     */
    public OneLevelStat getStatForLevel(int level) {
        if (level < 1 || level > 20) {
            throw new IndexOutOfBoundsException("Level must be between 1 and 20, got: " + level);
        }
        return data.get(level - 1);
    }

    /**
     * Returns an unmodifiable view of all level statistics ordered from level 1 to 20.
     *
     * @return list of {@link OneLevelStat}, length 20
     */
    public List<OneLevelStat> getData() {
        return Collections.unmodifiableList(data);
    }
}
