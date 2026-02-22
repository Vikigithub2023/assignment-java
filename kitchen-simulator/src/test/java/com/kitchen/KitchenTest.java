package com.kitchen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KitchenTest {
    @Test
    void placeThenPickup_logsActions() {
        AtomicLong now = new AtomicLong(0);
        Kitchen kitchen = new Kitchen(now::get);
        Order order = new Order("o1", "Soup", Order.Temperature.HOT, 300, 0.45);

        kitchen.placeOrder(order);
        kitchen.pickupOrder(order.getId());

        List<Action> ledger = kitchen.getLedgerSnapshot();
        assertEquals(2, ledger.size());
        assertEquals("place", ledger.get(0).getAction());
        assertEquals("pickup", ledger.get(1).getAction());
        assertTrue(ledger.get(0).getTimestamp() <= ledger.get(1).getTimestamp());
    }

    @Test
    void place_goesToShelf_whenIdealFull() {
        AtomicLong now = new AtomicLong(0);
        Kitchen kitchen = new Kitchen(now::get);

        for (int i = 0; i < 6; i++) {
            kitchen.placeOrder(new Order("hot-" + i, "Hot" + i, Order.Temperature.HOT, 300, 1.0));
        }

        int before = kitchen.getLedgerSnapshot().size();
        kitchen.placeOrder(new Order("hot-extra", "HotExtra", Order.Temperature.HOT, 300, 1.0));

        List<Action> ledger = kitchen.getLedgerSnapshot();
        assertEquals(before + 1, ledger.size());
        Action a = ledger.get(ledger.size() - 1);
        assertEquals("place", a.getAction());
        assertEquals("shelf", a.getTarget());
    }

    @Test
    void place_movesHotFromShelfToHeater_thenPlacesNewOrder() {
        AtomicLong now = new AtomicLong(0);
        Kitchen kitchen = new Kitchen(now::get);

        // Fill cooler so new cold order can't go to ideal storage.
        for (int i = 0; i < 6; i++) {
            kitchen.placeOrder(new Order("cold-" + i, "Cold" + i, Order.Temperature.COLD, 300, 1.0));
        }

        // Fill heater so additional HOT goes to shelf.
        for (int i = 0; i < 6; i++) {
            kitchen.placeOrder(new Order("heater-hot-" + i, "HeaterHot" + i, Order.Temperature.HOT, 300, 1.0));
        }

        // Fill shelf with HOT orders; one has much lower shelf life so it should be selected to move.
        kitchen.placeOrder(new Order("shelf-hot-min", "ShelfHotMin", Order.Temperature.HOT, 1, 1.0));
        for (int i = 0; i < 11; i++) {
            kitchen.placeOrder(new Order("shelf-hot-" + i, "ShelfHot" + i, Order.Temperature.HOT, 100, 1.0));
        }

        // Create a free spot in heater.
        kitchen.pickupOrder("heater-hot-0");

        int before = kitchen.getLedgerSnapshot().size();
        kitchen.placeOrder(new Order("cold-new", "ColdNew", Order.Temperature.COLD, 300, 1.0));

        List<Action> ledger = kitchen.getLedgerSnapshot();
        assertEquals(before + 2, ledger.size());

        Action move = ledger.get(ledger.size() - 2);
        Action place = ledger.get(ledger.size() - 1);

        assertEquals("move", move.getAction());
        assertEquals("heater", move.getTarget());
        assertEquals("shelf-hot-min", move.getId());

        assertEquals("place", place.getAction());
        assertEquals("cold-new", place.getId());
        assertEquals("shelf", place.getTarget());
    }

    @Test
    void place_discardsFromShelf_whenNoMovesPossible_thenPlacesNewOrder() {
        AtomicLong now = new AtomicLong(0);
        Kitchen kitchen = new Kitchen(now::get);

        // Fill heater and cooler; shelf will contain only FROZEN so no HOT/COLD moves are possible.
        for (int i = 0; i < 6; i++) {
            kitchen.placeOrder(new Order("hot-" + i, "Hot" + i, Order.Temperature.HOT, 300, 1.0));
            kitchen.placeOrder(new Order("cold-" + i, "Cold" + i, Order.Temperature.COLD, 300, 1.0));
        }

        // Fill freezer so additional FROZEN goes to shelf.
        for (int i = 0; i < 6; i++) {
            kitchen.placeOrder(new Order("freezer-" + i, "Freezer" + i, Order.Temperature.FROZEN, 300, 1.0));
        }

        // Fill shelf with FROZEN. Make one expire soonest so it should be discarded first.
        kitchen.placeOrder(new Order("shelf-frozen-min", "ShelfFrozenMin", Order.Temperature.FROZEN, 1, 1.0));
        for (int i = 0; i < 11; i++) {
            kitchen.placeOrder(new Order("shelf-frozen-" + i, "ShelfFrozen" + i, Order.Temperature.FROZEN, 100, 1.0));
        }

        int before = kitchen.getLedgerSnapshot().size();
        kitchen.placeOrder(new Order("hot-new", "HotNew", Order.Temperature.HOT, 300, 1.0));

        List<Action> ledger = kitchen.getLedgerSnapshot();
        assertEquals(before + 2, ledger.size());

        Action discard = ledger.get(ledger.size() - 2);
        Action place = ledger.get(ledger.size() - 1);

        assertEquals("discard", discard.getAction());
        assertEquals("shelf-frozen-min", discard.getId());
        assertEquals("shelf", discard.getTarget());

        assertEquals("place", place.getAction());
        assertEquals("hot-new", place.getId());
        assertEquals("shelf", place.getTarget());
    }

    @Test
    void pickup_discards_whenExpired() {
        AtomicLong now = new AtomicLong(0);
        Kitchen kitchen = new Kitchen(now::get);

        Order order = new Order("exp", "Expired", Order.Temperature.HOT, 0, 1.0);
        kitchen.placeOrder(order);

        now.set(1_000_000L);
        kitchen.pickupOrder(order.getId());

        List<Action> ledger = kitchen.getLedgerSnapshot();
        assertEquals(2, ledger.size());
        assertEquals("place", ledger.get(0).getAction());
        assertEquals("discard", ledger.get(1).getAction());
        assertEquals("heater", ledger.get(1).getTarget());
    }

    @Test
    void pickup_missing_doesNothing() {
        AtomicLong now = new AtomicLong(0);
        Kitchen kitchen = new Kitchen(now::get);

        kitchen.placeOrder(new Order("o1", "Soup", Order.Temperature.HOT, 300, 1.0));
        int before = kitchen.getLedgerSnapshot().size();
        kitchen.pickupOrder("missing");
        assertEquals(before, kitchen.getLedgerSnapshot().size());
    }
}
