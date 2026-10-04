package com.multitasks.store;

/** Locks the current item. After save, the old code must leave the frame before reuse. */
public final class ScanGate {
    private String locked = "", blocked = "";
    private int absentFrames;
    public boolean accept(String code) {
        if (code == null || code.isEmpty()) {
            if (++absentFrames >= 3) blocked = "";
            return false;
        }
        absentFrames = 0;
        if (!locked.isEmpty() || code.equals(blocked)) return false;
        locked = code;
        return true;
    }
    public void saved(String code) { blocked = code; locked = ""; absentFrames = 0; }
    public void clear() { locked = ""; blocked = ""; absentFrames = 0; }
}
