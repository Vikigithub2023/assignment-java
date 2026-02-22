package com.kitchen;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ShelfManagerTest {
    @Test
    void removeOrder_usesLazyDeletion() {
        AtomicLong now = new AtomicLong(0);
        ShelfManager shelf = new ShelfManager(12, now::get);

        StoredOrder a = new StoredOrder(new Order("a", "A", Order.Temperature.HOT, 10, 1.0), StorageType.SHELF, 0);
        StoredOrder b = new StoredOrder(new Order("b", "B", Order.Temperature.HOT, 10, 1.0), StorageType.SHELF, 0);

        shelf.addOrder(a);
        shelf.addOrder(b);
        shelf.removeOrder("a");

        StoredOrder next = shelf.pollNextToExpire();
        assertNotNull(next);
        assertEquals("b", next.getOrder().getId());
        assertEquals(0, shelf.size());
    }

    @Test
    void pollNextToExpire_returnsSoonestExpirationBasedOnShelfLife() {
        AtomicLong now = new AtomicLong(0);
        ShelfManager shelf = new ShelfManager(12, now::get);

        StoredOrder longLife = new StoredOrder(new Order("l", "Long", Order.Temperature.HOT, 100, 1.0), StorageType.SHELF, 0);
        StoredOrder shortLife = new StoredOrder(new Order("s", "Short", Order.Temperature.HOT, 1, 1.0), StorageType.SHELF, 0);

        shelf.addOrder(longLife);
        shelf.addOrder(shortLife);

        StoredOrder next = shelf.pollNextToExpire();
        assertNotNull(next);
        assertEquals("s", next.getOrder().getId());
    }
}

