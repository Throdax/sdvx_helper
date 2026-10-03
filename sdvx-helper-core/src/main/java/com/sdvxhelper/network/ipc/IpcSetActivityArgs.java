package com.sdvxhelper.network.ipc;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Represents the {@code args} block of a Discord IPC {@code SET_ACTIVITY}
 * command frame.
 *
 * <p>
 * Serialises to:
 * </p>
 * 
 * <pre>{@code {"pid":<pid>,"activity":{...}}}</pre>
 *
 * <p>
 * When {@link #activity} is {@code null} the field is serialised as
 * {@code "activity":null}, which instructs Discord to clear the presence.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class IpcSetActivityArgs {

    /** PID of the process setting the activity. */
    @JsonbProperty("pid")
    private long pid;

    /**
     * The activity payload to display, or {@code null} to clear the presence.
     */
    @JsonbProperty("activity")
    private IpcActivity activity;

    /** No-argument constructor required by JSON-B. */
    public IpcSetActivityArgs() {
    }

    /**
     * Constructs a {@code SET_ACTIVITY} args block.
     *
     * @param pid
     *            the PID of the calling process
     * @param activity
     *            the activity to display, or {@code null} to clear
     */
    public IpcSetActivityArgs(long pid, IpcActivity activity) {
        this.pid = pid;
        this.activity = activity;
    }

    /**
     * Returns the PID of the process setting the activity.
     *
     * @return the process ID
     */
    public long getPid() {
        return pid;
    }

    /**
     * Sets the PID of the process setting the activity.
     *
     * @param pid
     *            the process ID to set
     */
    public void setPid(long pid) {
        this.pid = pid;
    }

    /**
     * Returns the activity payload, or {@code null} if the presence is being
     * cleared.
     *
     * @return the {@link IpcActivity}, or {@code null}
     */
    public IpcActivity getActivity() {
        return activity;
    }

    /**
     * Sets the activity payload.
     *
     * @param activity
     *            the {@link IpcActivity} to set, or {@code null} to clear
     */
    public void setActivity(IpcActivity activity) {
        this.activity = activity;
    }
}
