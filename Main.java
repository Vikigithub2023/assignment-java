import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> cli = parseArgs(args);
        String ordersUrl = required(cli, "ordersUrl");
        String solveUrl = required(cli, "solveUrl");

        long placeEveryMillis = Long.parseLong(cli.getOrDefault("placeEveryMillis", "500"));
        long maxAwaitSeconds = Long.parseLong(cli.getOrDefault("awaitSeconds", "60"));

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        List<Order> orders = fetchOrders(http, ordersUrl);
        Kitchen kitchen = new Kitchen();

        CountDownLatch latch = new CountDownLatch(orders.size());
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
        AtomicInteger index = new AtomicInteger(0);

        ScheduledFuture<?> placeFuture = scheduler.scheduleAtFixedRate(() -> {
            int i = index.getAndIncrement();
            if (i >= orders.size()) {
                return;
            }

            Order order = orders.get(i);
            kitchen.placeOrder(order);

            long pickupDelaySeconds = ThreadLocalRandom.current().nextLong(4, 9); // 4–8 inclusive
            scheduler.schedule(() -> {
                try {
                    kitchen.pickupOrder(order.getId());
                } finally {
                    latch.countDown();
                }
            }, pickupDelaySeconds, TimeUnit.SECONDS);
        }, 0, placeEveryMillis, TimeUnit.MILLISECONDS);

        // Stop placing once all orders have been scheduled.
        scheduler.scheduleWithFixedDelay(() -> {
            if (index.get() >= orders.size()) {
                placeFuture.cancel(false);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        boolean done = latch.await(maxAwaitSeconds, TimeUnit.SECONDS);
        placeFuture.cancel(false);
        scheduler.shutdown();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);

        if (!done) {
            throw new IllegalStateException("Timed out waiting for pickups (" + maxAwaitSeconds + "s)");
        }

        List<Action> actions = kitchen.getLedgerSnapshot();
        postSolution(http, solveUrl, actions);
    }

    private static List<Order> fetchOrders(HttpClient http, String ordersUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ordersUrl))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("GET orders failed: HTTP " + resp.statusCode());
        }

        Object root = Json.parse(resp.body());
        Object ordersNode = root;
        if (root instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) root;
            if (map.containsKey("orders")) {
                ordersNode = map.get("orders");
            }
        }

        if (!(ordersNode instanceof List<?>)) {
            throw new IllegalArgumentException("Orders payload must be an array (or {\"orders\": [...]})");
        }

        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) ordersNode;
        List<Order> orders = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            orders.add(orderFromMap(m));
        }
        return orders;
    }

    private static void postSolution(HttpClient http, String solveUrl, List<Action> actions) throws IOException, InterruptedException {
        List<Object> actionsJson = new ArrayList<>(actions.size());
        for (Action a : actions) {
            Map<String, Object> m = new HashMap<>();
            m.put("timestampMicros", a.getTimestampMicros());
            m.put("orderId", a.getOrderId());
            m.put("action", a.getAction());
            m.put("target", a.getTarget());
            actionsJson.add(m);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("actions", actionsJson);

        String body = Json.stringify(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(solveUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("POST solve failed: HTTP " + resp.statusCode() + " body=" + resp.body());
        }
    }

    private static Order orderFromMap(Map<String, Object> m) {
        String id = asString(firstNonNull(m, "id", "orderId"));
        String name = asString(firstNonNull(m, "name"));
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order missing id");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Order missing name for id=" + id);
        }
        Order.Temperature temp = parseTemperature(asString(firstNonNull(m, "temp", "temperature")));
        int shelfLifeSeconds = asInt(firstNonNull(m, "shelfLifeSeconds", "shelfLife", "shelfLifeSec"));
        double decayRate = asDouble(firstNonNull(m, "decayRate", "decay_rate"));
        return new Order(id, name, temp, shelfLifeSeconds, decayRate);
    }

    private static Object firstNonNull(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            if (m.containsKey(k) && m.get(k) != null) {
                return m.get(k);
            }
        }
        return null;
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        return String.valueOf(o);
    }

    private static int asInt(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o == null) {
            throw new IllegalArgumentException("Missing int field");
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private static double asDouble(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o == null) {
            throw new IllegalArgumentException("Missing double field");
        }
        return Double.parseDouble(String.valueOf(o));
    }

    private static Order.Temperature parseTemperature(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Missing temperature");
        }
        String v = s.trim().toUpperCase();
        if (v.equals("HOT")) {
            return Order.Temperature.HOT;
        }
        if (v.equals("COLD")) {
            return Order.Temperature.COLD;
        }
        if (v.equals("FROZEN")) {
            return Order.Temperature.FROZEN;
        }
        throw new IllegalArgumentException("Unknown temperature: " + s);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                continue;
            }
            String key = a.substring(2);
            String value = "true";
            int eq = key.indexOf('=');
            if (eq >= 0) {
                value = key.substring(eq + 1);
                key = key.substring(0, eq);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            out.put(key, value);
        }
        return out;
    }

    private static String required(Map<String, String> cli, String key) {
        String v = cli.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required arg --" + key);
        }
        return v;
    }
}
