import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Kitchen {
    private static final int IDEAL_CAPACITY = 6;
    private static final int SHELF_CAPACITY = 12;

    private final ReentrantLock lock = new ReentrantLock();

    private final List<StoredOrder> heater = new ArrayList<>();
    private final List<StoredOrder> cooler = new ArrayList<>();
    private final List<StoredOrder> freezer = new ArrayList<>();
    private final ShelfManager shelf = new ShelfManager(SHELF_CAPACITY);

    private final Map<String, StoredOrder> allOrders = new HashMap<>();
    private final List<Action> ledger = new ArrayList<>();

    public void placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order cannot be null");
        }

        lock.lock();
        try {
            long nowMicros = nowMicros();

            while (true) {
                StorageType idealStorage = idealStorageFor(order);
                List<StoredOrder> idealList = storageList(idealStorage);

                if (idealList.size() < IDEAL_CAPACITY) {
                    StoredOrder storedOrder = new StoredOrder(order, idealStorage, nowMicros);
                    idealList.add(storedOrder);
                    allOrders.put(order.getId(), storedOrder);
                    ledger.add(new Action(Action.Type.PLACE, order.getId(), null, idealStorage, nowMicros));
                    return;
                }

                if (!shelf.isFull()) {
                    StoredOrder storedOrder = new StoredOrder(order, StorageType.SHELF, nowMicros);
                    shelf.addOrder(storedOrder);
                    allOrders.put(order.getId(), storedOrder);
                    ledger.add(new Action(Action.Type.PLACE, order.getId(), null, StorageType.SHELF, nowMicros));
                    return;
                }

                if (!tryMoveShelfOrder(StorageType.HEATER)) {
                    if (!tryMoveShelfOrder(StorageType.COOLER)) {
                        discardFromShelf();
                    }
                }

                nowMicros = nowMicros();
            }
        } finally {
            lock.unlock();
        }
    }

    public void pickupOrder(String orderId) {
        if (orderId == null) {
            return;
        }

        lock.lock();
        try {
            StoredOrder storedOrder = allOrders.remove(orderId);
            if (storedOrder == null) {
                return;
            }

            long nowMicros = nowMicros();
            StorageType storageType = storedOrder.getStorageType();

            if (storageType == StorageType.SHELF) {
                shelf.removeOrder(orderId);
                ledger.add(new Action(Action.Type.PICKUP, orderId, StorageType.SHELF, null, nowMicros));
                return;
            }

            removeFromListById(storageList(storageType), orderId);
            storedOrder.deactivate();
            ledger.add(new Action(Action.Type.PICKUP, orderId, storageType, null, nowMicros));
        } finally {
            lock.unlock();
        }
    }

    private void discardFromShelf() {
        long nowMicros = nowMicros();
        StoredOrder discarded = shelf.pollNextToExpire();
        if (discarded == null) {
            return;
        }

        allOrders.remove(discarded.getOrder().getId());
        ledger.add(new Action(Action.Type.DISCARD, discarded.getOrder().getId(), StorageType.SHELF, null, nowMicros));
    }

    private boolean tryMoveShelfOrder(StorageType targetStorage) {
        if (targetStorage == null) {
            return false;
        }
        if (targetStorage != StorageType.HEATER && targetStorage != StorageType.COOLER && targetStorage != StorageType.FREEZER) {
            return false;
        }

        List<StoredOrder> targetList = storageList(targetStorage);
        if (targetList.size() >= IDEAL_CAPACITY) {
            return false;
        }

        long nowMicros = nowMicros();

        StoredOrder candidate = null;
        for (StoredOrder storedOrder : allOrders.values()) {
            if (!storedOrder.isActive()) {
                continue;
            }
            if (storedOrder.getStorageType() != StorageType.SHELF) {
                continue;
            }
            if (!targetStorage.isIdealFor(storedOrder.getOrder())) {
                continue;
            }

            storedOrder.updateFreshness(nowMicros);
            if (!storedOrder.isActive()) {
                continue;
            }

            if (candidate == null || storedOrder.getRemainingFreshnessMicros() < candidate.getRemainingFreshnessMicros()) {
                candidate = storedOrder;
            }
        }

        if (candidate == null) {
            return false;
        }

        Order order = candidate.getOrder();
        String orderId = order.getId();

        shelf.removeOrder(orderId);

        StoredOrder moved = new StoredOrder(
                order,
                targetStorage,
                candidate.getPlacedTimeMicros(),
                nowMicros,
                candidate.getRemainingFreshnessMicros()
        );

        targetList.add(moved);
        allOrders.put(orderId, moved);
        ledger.add(new Action(Action.Type.MOVE, orderId, StorageType.SHELF, targetStorage, nowMicros));
        return true;
    }

    private static StorageType idealStorageFor(Order order) {
        if (order.getTemperature() == Order.Temperature.HOT) {
            return StorageType.HEATER;
        }
        if (order.getTemperature() == Order.Temperature.COLD) {
            return StorageType.COOLER;
        }
        return StorageType.FREEZER;
    }

    private List<StoredOrder> storageList(StorageType storageType) {
        if (storageType == StorageType.HEATER) {
            return heater;
        }
        if (storageType == StorageType.COOLER) {
            return cooler;
        }
        if (storageType == StorageType.FREEZER) {
            return freezer;
        }
        throw new IllegalArgumentException("No list for storageType=" + storageType);
    }

    private static void removeFromListById(List<StoredOrder> list, String orderId) {
        for (Iterator<StoredOrder> it = list.iterator(); it.hasNext(); ) {
            StoredOrder so = it.next();
            if (so.getOrder().getId().equals(orderId)) {
                it.remove();
                so.deactivate();
                return;
            }
        }
    }

    private static long nowMicros() {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    }
}
