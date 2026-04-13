package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB root element containing the complete song/jacket database.
 *
 * <p>Serialised to / deserialised from {@code musiclist.xml}. Replaces the
 * Python {@code musiclist.pkl} pickle file.</p>
 *
 * <p>The original Python structure is a nested dict:</p>
 * <pre>{@code
 * {
 *   'jacket':       { difficulty: { title: hash } },
 *   'info':         { difficulty: { title: hash } },
 *   'jacket_sha':   { difficulty: { title: sha  } },
 *   'titles':       { title: [artist, bpm, lv_nov, lv_adv, lv_exh, lv_append] },
 *   'gradeS_lv17':  { title: tier },
 *   'gradeS_lv18':  { title: tier },
 *   'gradeS_lv19':  { title: tier }
 * }
 * }</pre>
 *
 * <p>Each nested dict is represented here as a list of typed JAXB elements to
 * work around JAXB's lack of native support for
 * {@code Map<String, Map<String, String>>}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "MusicList")
@XmlAccessorType(XmlAccessType.FIELD)
public class MusicList {

    /**
     * Jacket perceptual hashes per difficulty tier. Corresponds to Python
     * {@code musiclist['jacket']}.
     */
    @XmlElement(name = "jacketGroup")
    private List<DifficultyHashGroup> jacket = new ArrayList<>();

    /**
     * Info-bar perceptual hashes per difficulty tier. Corresponds to Python
     * {@code musiclist['info']}.
     */
    @XmlElement(name = "infoGroup")
    private List<DifficultyHashGroup> info = new ArrayList<>();

    /**
     * Jacket SHA-256 hashes per difficulty tier. Corresponds to Python
     * {@code musiclist['jacket_sha']}.
     */
    @XmlElement(name = "jacketShaGroup")
    private List<DifficultyHashGroup> jacketSha = new ArrayList<>();

    /**
     * Song metadata indexed by title. Corresponds to Python
     * {@code musiclist['titles']}.
     */
    @XmlElement(name = "song")
    private List<SongInfoEntry> titles = new ArrayList<>();

    /**
     * Grade-S tier tables for levels 17–19. Corresponds to Python
     * {@code musiclist['gradeS_lv17']}, etc.
     */
    @XmlElement(name = "gradeSTable")
    private List<GradeSEntry> gradeSTable = new ArrayList<>();

    /**
     * Returns the jacket perceptual hash groups, one per difficulty tier.
     *
     * @return the list of jacket {@link DifficultyHashGroup} entries
     */
    public List<DifficultyHashGroup> getJacket() {
        return jacket;
    }

    /**
     * Sets the jacket perceptual hash groups, one per difficulty tier.
     *
     * @param jacket the list of jacket {@link DifficultyHashGroup} entries to set
     */
    public void setJacket(List<DifficultyHashGroup> jacket) {
        this.jacket = jacket;
    }

    /**
     * Returns the info-bar perceptual hash groups, one per difficulty tier.
     *
     * @return the list of info-bar {@link DifficultyHashGroup} entries
     */
    public List<DifficultyHashGroup> getInfo() {
        return info;
    }

    /**
     * Sets the info-bar perceptual hash groups, one per difficulty tier.
     *
     * @param info the list of info-bar {@link DifficultyHashGroup} entries to set
     */
    public void setInfo(List<DifficultyHashGroup> info) {
        this.info = info;
    }

    /**
     * Returns the jacket SHA-256 hash groups, one per difficulty tier.
     *
     * @return the list of jacket SHA {@link DifficultyHashGroup} entries
     */
    public List<DifficultyHashGroup> getJacketSha() {
        return jacketSha;
    }

    /**
     * Sets the jacket SHA-256 hash groups, one per difficulty tier.
     *
     * @param jacketSha the list of jacket SHA {@link DifficultyHashGroup} entries to set
     */
    public void setJacketSha(List<DifficultyHashGroup> jacketSha) {
        this.jacketSha = jacketSha;
    }

    /**
     * Returns the song metadata entries indexed by title.
     *
     * @return the list of {@link SongInfoEntry} song metadata entries
     */
    public List<SongInfoEntry> getTitles() {
        return titles;
    }

    /**
     * Sets the song metadata entries indexed by title.
     *
     * @param titles the list of {@link SongInfoEntry} song metadata entries to set
     */
    public void setTitles(List<SongInfoEntry> titles) {
        this.titles = titles;
    }

    /**
     * Returns the grade-S tier table entries for levels 17–19.
     *
     * @return the list of {@link GradeSEntry} grade-S tier table entries
     */
    public List<GradeSEntry> getGradeSTable() {
        return gradeSTable;
    }

    /**
     * Sets the grade-S tier table entries for levels 17–19.
     *
     * @param gradeSTable the list of {@link GradeSEntry} grade-S tier table entries to set
     */
    public void setGradeSTable(List<GradeSEntry> gradeSTable) {
        this.gradeSTable = gradeSTable;
    }
}
