public class Action {
    public enum Type {
        PLACE,
        PICKUP,
        MOVE,
        DISCARD
    }

    private final Type type;
    private final String orderId;
    private final StorageType from;
    private final StorageType to;
    private final long timeMicros;

    public Action(Type type, String orderId, StorageType from, StorageType to, long timeMicros) {
        this.type = type;
        this.orderId = orderId;
        this.from = from;
        this.to = to;
        this.timeMicros = timeMicros;
    }

    public Type getType() {
        return type;
    }

    public String getOrderId() {
        return orderId;
    }

    public StorageType getFrom() {
        return from;
    }

    public StorageType getTo() {
        return to;
    }

    public long getTimeMicros() {
        return timeMicros;
    }

    @Override
    public String toString() {
        return "Action{" +
                "type=" + type +
                ", orderId='" + orderId + '\'' +
                ", from=" + from +
                ", to=" + to +
                ", timeMicros=" + timeMicros +
                '}';
    }
}
