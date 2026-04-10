package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JAXB root element containing the complete song/jacket database.
 *
 * <p>Serialised to / deserialised from {@code musiclist.xml}.
 * Replaces the Python {@code musiclist.pkl} pickle file.
 *
 * <p>The original Python structure is a nested dict:
 * <pre>{@code
 * {
 *   'jacket':     { difficulty: { title: hash } },
 *   'info':       { difficulty: { title: hash } },
 *   'jacket_sha': { difficulty: { title: sha  } },
 *   'titles':     { title: [artist, bpm, lv_nov, lv_adv, lv_exh, lv_append] },
 *   'gradeS_lv17': { title: tier },
 *   'gradeS_lv18': { title: tier },
 *   'gradeS_lv19': { title: tier }
 * }
 * }</pre>
 *
 * <p>Each nested dict is represented here as a list of typed JAXB elements to work
 * around JAXB's lack of native support for {@code Map<String, Map<String, String>>}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "MusicList")
@XmlAccessorType(XmlAccessType.FIELD)
public class MusicList {

    /**
     * Jacket perceptual hashes per difficulty tier.
     * Corresponds to Python {@code musiclist['jacket']}.
     */
    @XmlElement(name = "jacketGroup")
    private List<DifficultyHashGroup> jacket = new ArrayList<>();

    /**
     * Info-bar perceptual hashes per difficulty tier.
     * Corresponds to Python {@code musiclist['info']}.
     */
    @XmlElement(name = "infoGroup")
    private List<DifficultyHashGroup> info = new ArrayList<>();

    /**
     * Jacket SHA-256 hashes per difficulty tier.
     * Corresponds to Python {@code musiclist['jacket_sha']}.
     */
    @XmlElement(name = "jacketShaGroup")
    private List<DifficultyHashGroup> jacketSha = new ArrayList<>();

    /**
     * Song metadata indexed by title.
     * Corresponds to Python {@code musiclist['titles']}.
     */
    @XmlElement(name = "song")
    private List<SongInfoEntry> titles = new ArrayList<>();

    /**
     * Grade-S tier tables for levels 17–19.
     * Corresponds to Python {@code musiclist['gradeS_lv17']}, etc.
     */
    @XmlElement(name = "gradeSTable")
    private List<GradeSEntry> gradeSTable = new ArrayList<>();

    /** @return jacket hash groups */
    public List<DifficultyHashGroup> getJacket() { return jacket; }

    /** @param jacket jacket hash groups */
    public void setJacket(List<DifficultyHashGroup> jacket) { this.jacket = jacket; }

    /** @return info-bar hash groups */
    public List<DifficultyHashGroup> getInfo() { return info; }

    /** @param info info-bar hash groups */
    public void setInfo(List<DifficultyHashGroup> info) { this.info = info; }

    /** @return jacket SHA hash groups */
    public List<DifficultyHashGroup> getJacketSha() { return jacketSha; }

    /** @param jacketSha jacket SHA hash groups */
    public void setJacketSha(List<DifficultyHashGroup> jacketSha) { this.jacketSha = jacketSha; }

    /** @return song info entries */
    public List<SongInfoEntry> getTitles() { return titles; }

    /** @param titles song info entries */
    public void setTitles(List<SongInfoEntry> titles) { this.titles = titles; }

    /** @return grade-S table entries */
    public List<GradeSEntry> getGradeSTable() { return gradeSTable; }

    /** @param gradeSTable grade-S table entries */
    public void setGradeSTable(List<GradeSEntry> gradeSTable) { this.gradeSTable = gradeSTable; }
}
