package com.kitchen;

public class Action {
    private final long timestamp;
    private final String id;
    private final String action;
    private final String target;

    public Action(long timestamp, String id, String action, String target) {
        this.timestamp = timestamp;
        this.id = id;
        this.action = action;
        this.target = target;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "Action{" +
                "timestamp=" + timestamp +
                ", id='" + id + '\'' +
                ", action='" + action + '\'' +
                ", target='" + target + '\'' +
                '}';
    }
}
