package com.kitchen;

public class Action {
    private final long timestampMicros;
    private final String orderId;
    private final String action;
    private final String target;

    public Action(long timestampMicros, String orderId, String action, String target) {
        this.timestampMicros = timestampMicros;
        this.orderId = orderId;
        this.action = action;
        this.target = target;
    }

    public long getTimestampMicros() {
        return timestampMicros;
    }

    public String getOrderId() {
        return orderId;
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
                "timestampMicros=" + timestampMicros +
                ", orderId='" + orderId + '\'' +
                ", action='" + action + '\'' +
                ", target='" + target + '\'' +
                '}';
    }
}

