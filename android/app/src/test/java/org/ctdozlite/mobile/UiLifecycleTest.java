package org.ctdozlite.mobile;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiLifecycleTest {
    @Test public void stoppedAndResumedActivityRejectsOldResults() {
        UiLifecycle gate = new UiLifecycle();
        assertFalse(gate.accepts(gate.ticket()));
        gate.resume(); long old = gate.ticket(); assertTrue(gate.accepts(old));
        gate.stop(); assertFalse(gate.accepts(old)); assertFalse(gate.accepts(gate.ticket()));
        gate.resume(); assertFalse(gate.accepts(old)); assertTrue(gate.accepts(gate.ticket()));
    }
    @Test public void switchingLoginScreenInvalidatesOldRestoreAndReads() {
        UiLifecycle gate = new UiLifecycle(); gate.resume();
        long old = gate.ticket(); gate.invalidate();
        assertFalse(gate.accepts(old)); assertTrue(gate.accepts(gate.ticket()));
    }
}
