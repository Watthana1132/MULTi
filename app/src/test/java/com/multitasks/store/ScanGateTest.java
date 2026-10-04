package com.multitasks.store;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScanGateTest {
    @Test public void savedBarcodeCannotImmediatelyDuplicate() {
        ScanGate gate = new ScanGate();
        assertTrue(gate.accept("8850123456789"));
        assertFalse(gate.accept("12345678"));
        gate.saved("8850123456789");
        assertFalse(gate.accept("8850123456789"));
        gate.accept(null); gate.accept(null); gate.accept(null);
        assertTrue(gate.accept("8850123456789"));
    }
    @Test public void nextDifferentProductIsImmediatelyAccepted() {
        ScanGate gate = new ScanGate();
        gate.accept("first"); gate.saved("first");
        assertTrue(gate.accept("second"));
    }
}
