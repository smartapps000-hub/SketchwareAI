package com.sketchware.ai.project;

/** The build fixed its own inputs (e.g. enabled a missing library) and has to start over once. */
public final class RebuildRequested extends RuntimeException {
    public RebuildRequested() {
        super("Rebuild requested", null, false, false);
    }
}
