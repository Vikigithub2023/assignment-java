package com.kitchen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.concurrent.locks.ReentrantLock;

public class Kitchen {
    private static final int IDEAL_CAPACITY = 6;
    private static final int SHELF_CAPACITY = 12;

    private final ReentrantLock lock = new ReentrantLock();
    private final LongSupplier nowMicros;

    private final List<StoredOrder> heater = new ArrayList<>();
    private final List<StoredOrder> cooler = new ArrayList<>();
    private final List<StoredOrder> freezer = new ArrayList<>();
    private final ShelfManager shelf;

    private final Map<String, StoredOrder> allOrders = new HashMap<>();
    private final List<Action> ledger = new ArrayList<>();

    private static String location(StorageType storageType) {
        return storageType.name().toLowerCase();
    }

    public Kitchen() {
        this(() -> TimeUnit.NANOSECONDS.toMicros(System.nanoTime()));
    }

    public Kitchen(LongSupplier nowMicros) {
        this.nowMicros = nowMicros;
        this.shelf = new ShelfManager(SHELF_CAPACITY, nowMicros);
    }

    public List<Action> getLedgerSnapshot() {
        lock.lock();
        try {
            return new ArrayList<>(ledger);
        } finally {
            lock.unlock();
        }
    }

    public void placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order cannot be null");
        }

        lock.lock();
        try {
            long nowMicros = nowMicros();

            while (true) {
                StorageType idealStorage = idealStorageFor(order);
                if (idealStorage == StorageType.SHELF) {
                    if (!shelf.isFull()) {
                        StoredOrder storedOrder = new StoredOrder(order, StorageType.SHELF, nowMicros);
                        shelf.addOrder(storedOrder);
                        allOrders.put(order.getId(), storedOrder);
                        ledger.add(new Action(nowMicros, order.getId(), "place", location(StorageType.SHELF)));
                        return;
                    }
                } else {
                    List<StoredOrder> idealList = storageList(idealStorage);

                    if (idealList.size() < IDEAL_CAPACITY) {
                        StoredOrder storedOrder = new StoredOrder(order, idealStorage, nowMicros);
                        idealList.add(storedOrder);
                        allOrders.put(order.getId(), storedOrder);
                        ledger.add(new Action(nowMicros, order.getId(), "place", location(idealStorage)));
                        return;
                    }

                    if (!shelf.isFull()) {
                        StoredOrder storedOrder = new StoredOrder(order, StorageType.SHELF, nowMicros);
                        shelf.addOrder(storedOrder);
                        allOrders.put(order.getId(), storedOrder);
                        ledger.add(new Action(nowMicros, order.getId(), "place", location(StorageType.SHELF)));
                        return;
                    }
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
            StoredOrder storedOrder = allOrders.get(orderId);
            if (storedOrder == null) {
                return;
            }

            long nowMicros = nowMicros();
            StorageType storageType = storedOrder.getStorageType();

            storedOrder.updateFreshness(nowMicros);
            boolean expired = !storedOrder.isActive();

            if (storageType == StorageType.SHELF) {
                shelf.removeOrder(orderId);
            } else {
                removeFromListById(storageList(storageType), orderId);
            }

            allOrders.remove(orderId);

            if (expired) {
                ledger.add(new Action(nowMicros, orderId, "discard", location(storageType)));
            } else {
                ledger.add(new Action(nowMicros, orderId, "pickup", location(storageType)));
            }
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
        ledger.add(new Action(nowMicros, discarded.getOrder().getId(), "discard", location(StorageType.SHELF)));
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
        ledger.add(new Action(nowMicros, orderId, "move", location(targetStorage)));
        return true;
    }

    private static StorageType idealStorageFor(Order order) {
        if (order.getTemperature() == Order.Temperature.HOT) {
            return StorageType.HEATER;
        }
        if (order.getTemperature() == Order.Temperature.COLD) {
            return StorageType.COOLER;
        }
        if (order.getTemperature() == Order.Temperature.ROOM) {
            return StorageType.SHELF;
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

    private long nowMicros() {
        return nowMicros.getAsLong();
    }
}
