package com.serafinebot.p4.model.archive;

import java.io.IOException;

/**
 * Signals that a compressed archive is malformed or unsupported.
 */
public class ArchiveFormatException extends IOException {

    /**
     * Creates a new archive-format exception with the provided detail message.
     */
    public ArchiveFormatException(String message) {
        super(message);
    }
}
