import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

public class ShelfManager {
    private final int capacity;

    private final PriorityQueue<StoredOrder> expiringHeap;
    private final Map<String, StoredOrder> orderMap;

    public ShelfManager(int capacity) {
        this.capacity = capacity;
        this.expiringHeap = new PriorityQueue<>(Comparator.comparingLong(ShelfManager::estimatedExpirationMicros));
        this.orderMap = new HashMap<>();
    }

    public ShelfManager() {
        this(Integer.MAX_VALUE);
    }

    public void addOrder(StoredOrder storedOrder) {
        if (storedOrder == null) {
            throw new IllegalArgumentException("storedOrder cannot be null");
        }

        String orderId = storedOrder.getOrder().getId();
        StoredOrder existing = orderMap.put(orderId, storedOrder);
        if (existing != null) {
            existing.deactivate();
        }

        expiringHeap.add(storedOrder);
    }

    public void removeOrder(String orderId) {
        StoredOrder storedOrder = orderMap.remove(orderId);
        if (storedOrder != null) {
            storedOrder.deactivate();
        }
    }

    public StoredOrder pollNextToExpire() {
        long nowMicros = nowMicros();

        while (!expiringHeap.isEmpty()) {
            StoredOrder next = expiringHeap.poll();
            if (!next.isActive()) {
                continue;
            }

            // Ensure we don't return an entry that has already expired by time.
            if (next.isExpired(nowMicros)) {
                orderMap.remove(next.getOrder().getId());
                continue;
            }

            orderMap.remove(next.getOrder().getId());
            next.deactivate();
            return next;
        }

        return null;
    }

    public boolean isFull() {
        return size() >= capacity;
    }

    public int size() {
        return orderMap.size();
    }

    private static long estimatedExpirationMicros(StoredOrder storedOrder) {
        if (storedOrder == null) {
            return Long.MAX_VALUE;
        }
        if (!storedOrder.isActive()) {
            return Long.MAX_VALUE;
        }

        Order order = storedOrder.getOrder();
        if (order == null) {
            return Long.MAX_VALUE;
        }

        double remainingFreshnessMicros = storedOrder.getRemainingFreshnessMicros();
        if (remainingFreshnessMicros <= 0) {
            return storedOrder.getLastUpdatedMicros();
        }

        double decayRate = order.getDecayRate();
        if (decayRate <= 0) {
            return Long.MAX_VALUE;
        }

        double multiplier = storedOrder.getStorageType().isIdealFor(order) ? 1.0 : 2.0;
        double microsUntilExpire = remainingFreshnessMicros / (decayRate * multiplier);

        if (microsUntilExpire >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        long lastUpdated = storedOrder.getLastUpdatedMicros();
        long delta = (long) Math.ceil(microsUntilExpire);
        long candidate = lastUpdated + delta;
        return candidate < lastUpdated ? Long.MAX_VALUE : candidate;
    }

    private static long nowMicros() {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    }
}
