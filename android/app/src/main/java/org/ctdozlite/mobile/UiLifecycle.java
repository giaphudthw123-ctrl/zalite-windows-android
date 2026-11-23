package org.ctdozlite.mobile;

/** UI-thread owner for callbacks; an old Activity/request cannot publish into a newer screen. */
final class UiLifecycle {
    private long revision;
    private boolean active;
    void resume() { active = true; revision++; }
    void stop() { active = false; revision++; }
    void invalidate() { revision++; }
    long ticket() { return revision; }
    boolean accepts(long ticket) { return active && ticket == revision; }
}
