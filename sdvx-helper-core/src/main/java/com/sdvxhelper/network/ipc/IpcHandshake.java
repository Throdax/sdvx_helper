package com.sdvxhelper.network.ipc;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Payload sent as the first IPC frame ({@code opcode = 0}) to establish a
 * session with the Discord client.
 *
 * <p>Serialises to:</p>
 * <pre>{@code {"v":1,"client_id":"<id>"}}</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class IpcHandshake {

    /** Discord IPC protocol version. Always {@code 1}. */
    @JsonbProperty("v")
    private int version;

    /** Discord application client ID. */
    @JsonbProperty("client_id")
    private String clientId;

    /** No-argument constructor required by JSON-B. */
    public IpcHandshake() {
    }

    /**
     * Constructs a handshake payload for the given application.
     *
     * @param version  the IPC protocol version (always {@code 1})
     * @param clientId the Discord application client ID
     */
    public IpcHandshake(int version, String clientId) {
        this.version  = version;
        this.clientId = clientId;
    }

    /**
     * Returns the IPC protocol version.
     *
     * @return the protocol version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets the IPC protocol version.
     *
     * @param version the protocol version to set
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns the Discord application client ID.
     *
     * @return the client ID string
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the Discord application client ID.
     *
     * @param clientId the client ID string to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
