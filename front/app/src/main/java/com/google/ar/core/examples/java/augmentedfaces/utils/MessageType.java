package com.google.ar.core.examples.java.augmentedfaces.utils;

public enum MessageType {
    RECORD_START(1), RECORD_STOP(2), UNKNOWN(-1);
    private final int v;
    MessageType(int in) { v = in; }

    public static MessageType getFromInt(int in) {
        switch (in) {
            case 1:
                return RECORD_START;
            case 2:
                return RECORD_STOP;
            default:
                return UNKNOWN;
        }
    }

    public int toInt() { return v; }
}
