package com.jll.gemini.exception;

/**
 * A custom exception used to signal that the user has requested
 * to exit the interactive session. This is used for flow control
 * to cleanly break out of input loops.
 */

public class ExitException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public ExitException() {
        super("User requested to exit the application.");
    }
}