package com.kitchen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KitchenTest {
    @Test
    void placeThenPickup_logsActions() {
        Kitchen kitchen = new Kitchen();
        Order order = new Order("o1", "Soup", Order.Temperature.HOT, 300, 0.45);

        kitchen.placeOrder(order);
        kitchen.pickupOrder(order.getId());

        List<Action> ledger = kitchen.getLedgerSnapshot();
        assertEquals(2, ledger.size());
        assertEquals("place", ledger.get(0).getAction());
        assertEquals("pickup", ledger.get(1).getAction());
        assertTrue(ledger.get(0).getTimestampMicros() <= ledger.get(1).getTimestampMicros());
    }
}

