package com.sdvxhelper.network.ipc;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Represents a Discord IPC FRAME command ({@code opcode = 1}).
 *
 * <p>Serialises to:</p>
 * <pre>{@code
 * {
 *   "cmd":   "SET_ACTIVITY",
 *   "args":  { "pid": <pid>, "activity": { ... } },
 *   "nonce": "<uuid>"
 * }
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class IpcFrame {

    /** The IPC command name; always {@code "SET_ACTIVITY"} for presence updates. */
    @JsonbProperty("cmd")
    private String cmd;

    /** The command arguments block. */
    @JsonbProperty("args")
    private IpcSetActivityArgs args;

    /** A unique identifier for this frame used by Discord to correlate responses. */
    @JsonbProperty("nonce")
    private String nonce;

    /** No-argument constructor required by JSON-B. */
    public IpcFrame() {
    }

    /**
     * Constructs a command frame.
     *
     * @param cmd   the IPC command name (e.g. {@code "SET_ACTIVITY"})
     * @param args  the {@link IpcSetActivityArgs} arguments block
     * @param nonce a unique string to correlate the Discord response
     */
    public IpcFrame(String cmd, IpcSetActivityArgs args, String nonce) {
        this.cmd   = cmd;
        this.args  = args;
        this.nonce = nonce;
    }

    /**
     * Returns the IPC command name.
     *
     * @return the command name string
     */
    public String getCmd() {
        return cmd;
    }

    /**
     * Sets the IPC command name.
     *
     * @param cmd the command name string to set
     */
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    /**
     * Returns the command arguments block.
     *
     * @return the {@link IpcSetActivityArgs} block
     */
    public IpcSetActivityArgs getArgs() {
        return args;
    }

    /**
     * Sets the command arguments block.
     *
     * @param args the {@link IpcSetActivityArgs} block to set
     */
    public void setArgs(IpcSetActivityArgs args) {
        this.args = args;
    }

    /**
     * Returns the unique nonce string for this frame.
     *
     * @return the nonce string
     */
    public String getNonce() {
        return nonce;
    }

    /**
     * Sets the unique nonce string for this frame.
     *
     * @param nonce the nonce string to set
     */
    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
}
