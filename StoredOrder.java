import java.util.Objects;

public class StoredOrder {
    private static final long MICROS_PER_SECOND = 1_000_000L;

    private final Order order;
    private StorageType storageType;
    private final long placedTimeMicros;
    private long lastUpdatedMicros;
    private double remainingFreshnessMicros;
    private boolean active;

    public StoredOrder(Order order, StorageType storageType, long placedTimeMicros) {
        this.order = Objects.requireNonNull(order, "order");
        this.storageType = Objects.requireNonNull(storageType, "storageType");
        this.placedTimeMicros = placedTimeMicros;
        this.lastUpdatedMicros = placedTimeMicros;
        this.remainingFreshnessMicros = (double) order.getShelfLifeSeconds() * MICROS_PER_SECOND;
        this.active = true;
    }

    public Order getOrder() {
        return order;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public long getPlacedTimeMicros() {
        return placedTimeMicros;
    }

    public long getLastUpdatedMicros() {
        return lastUpdatedMicros;
    }

    public double getRemainingFreshnessMicros() {
        return remainingFreshnessMicros;
    }

    public boolean isActive() {
        return active;
    }

    public void updateFreshness(long currentMicros) {
        if (!active) {
            return;
        }

        long elapsedMicros = currentMicros - lastUpdatedMicros;
        if (elapsedMicros <= 0) {
            lastUpdatedMicros = currentMicros;
            return;
        }

        double multiplier = storageType.isIdealFor(order) ? 1.0 : 2.0;
        double decay = elapsedMicros * order.getDecayRate() * multiplier;

        remainingFreshnessMicros -= decay;
        lastUpdatedMicros = currentMicros;

        if (remainingFreshnessMicros <= 0) {
            remainingFreshnessMicros = 0;
            active = false;
        }
    }

    public boolean isExpired(long currentMicros) {
        updateFreshness(currentMicros);
        return !active;
    }

    public void moveTo(StorageType newStorage, long currentMicros) {
        Objects.requireNonNull(newStorage, "newStorage");
        updateFreshness(currentMicros);
        this.storageType = newStorage;
    }

    @Override
    public String toString() {
        return "StoredOrder{" +
                "order=" + order +
                ", storageType=" + storageType +
                ", placedTimeMicros=" + placedTimeMicros +
                ", lastUpdatedMicros=" + lastUpdatedMicros +
                ", remainingFreshnessMicros=" + remainingFreshnessMicros +
                ", active=" + active +
                '}';
    }
}
