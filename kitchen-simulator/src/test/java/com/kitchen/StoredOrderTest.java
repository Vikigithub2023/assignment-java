package com.kitchen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StoredOrderTest {
    @Test
    void updateFreshness_idealStorage_decays1x() {
        Order order = new Order("o1", "Soup", Order.Temperature.HOT, 10, 1.0);
        StoredOrder stored = new StoredOrder(order, StorageType.HEATER, 0);

        stored.updateFreshness(1_000_000L);

        assertTrue(stored.isActive());
        assertEquals(9_000_000.0, stored.getRemainingFreshnessMicros(), 0.0001);
    }

    @Test
    void updateFreshness_shelf_decays2x() {
        Order order = new Order("o1", "Soup", Order.Temperature.HOT, 10, 1.0);
        StoredOrder stored = new StoredOrder(order, StorageType.SHELF, 0);

        stored.updateFreshness(1_000_000L);

        assertTrue(stored.isActive());
        assertEquals(8_000_000.0, stored.getRemainingFreshnessMicros(), 0.0001);
    }

    @Test
    void updateFreshness_marksInactive_whenExpired() {
        Order order = new Order("o1", "Soup", Order.Temperature.HOT, 1, 10.0);
        StoredOrder stored = new StoredOrder(order, StorageType.HEATER, 0);

        stored.updateFreshness(1_000_000L);

        assertFalse(stored.isActive());
        assertEquals(0.0, stored.getRemainingFreshnessMicros(), 0.0001);
    }
}

