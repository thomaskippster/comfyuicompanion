package com.thomaskippster.comfyuicompanion.event;

public class ProgressUpdateEvent {
    private final int value;
    private final int max;

    public ProgressUpdateEvent(int value, int max) {
        this.value = value;
        this.max = max;
    }

    public int getValue() {
        return value;
    }

    public int getMax() {
        return max;
    }
}
