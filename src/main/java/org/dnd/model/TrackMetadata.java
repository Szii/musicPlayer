package org.dnd.model;

public record TrackMetadata(
        String title,
        String author,
        String uri,
        String identifier,
        boolean isStream,
        long durationMs
) {
    public Long durationSecondsOrNull() {
        return durationMs > 0 ? durationMs / 1000L : null;
    }
}
