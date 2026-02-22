package com.kitchen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final class OrdersResponse {
        private final List<Order> orders;
        private final String testId;

        private OrdersResponse(List<Order> orders, String testId) {
            this.orders = orders;
            this.testId = testId;
        }
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    private static int run(String[] args) {
        Map<String, String> cli = parseArgs(args);
        boolean getOnly = Boolean.parseBoolean(optional(cli, "getOnly", "GET_ONLY", "false"));
        String ordersUrl;
        String solveUrl = null;
        String authToken = optional(cli, "authToken", "AUTH_TOKEN");
        String authHeader = cli.getOrDefault("authHeader", System.getProperty("authHeader", System.getenv().getOrDefault("AUTH_HEADER", "Authorization")));
        String authScheme = cli.getOrDefault("authScheme", System.getProperty("authScheme", System.getenv().getOrDefault("AUTH_SCHEME", "Bearer")));

        try {
            ordersUrl = required(cli, "ordersUrl");
            if (!getOnly) {
                solveUrl = required(cli, "solveUrl");
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            return 2;
        }

        long placeEveryMillis = Long.parseLong(cli.getOrDefault("placeEveryMillis", "500"));
        long maxAwaitSeconds = Long.parseLong(cli.getOrDefault("awaitSeconds", "60"));

        URI ordersUri;
        URI solveUri = null;
        try {
            ordersUri = parseHttpUri(ordersUrl, "ordersUrl");
            if (!getOnly) {
                solveUri = parseHttpUri(solveUrl, "solveUrl");
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            return 2;
        }

        ExecutorService httpExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "http-client");
            t.setDaemon(true);
            return t;
        });

        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .executor(httpExecutor)
                    .build();

            OrdersResponse ordersResponse;
            try {
                ordersResponse = fetchOrders(http, ordersUri, authToken, authHeader, authScheme);
            } catch (IOException e) {
                System.err.println("Failed to fetch orders: " + e.getMessage());
                System.err.println("Is the server running and listening on that host/port?");
                return 1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while fetching orders.");
                return 1;
            } catch (RuntimeException e) {
                System.err.println("Failed to fetch orders: " + e.getMessage());
                return 1;
            }

            if (getOnly) {
                System.out.println("testId=" + (ordersResponse.testId == null ? "" : ordersResponse.testId));
                System.out.println("orders=" + ordersResponse.orders.size());
                for (int i = 0; i < Math.min(5, ordersResponse.orders.size()); i++) {
                    Order o = ordersResponse.orders.get(i);
                    System.out.println(o.getId() + " " + o.getTemperature() + " " + o.getShelfLifeSeconds());
                }
                return 0;
            }

            long startNanos = System.nanoTime();
            LongSupplier nowMicros = () -> TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
            Kitchen kitchen = new Kitchen(nowMicros);
            List<Order> orders = ordersResponse.orders;

            CountDownLatch latch = new CountDownLatch(orders.size());
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
            try {
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
                    System.err.println("Timed out waiting for pickups (" + maxAwaitSeconds + "s)");
                    return 1;
                }

                try {
                    postSolution(
                            http,
                            solveUri,
                            kitchen.getLedgerSnapshot(),
                            authToken,
                            authHeader,
                            authScheme,
                            ordersResponse.testId
                    );
                } catch (IOException e) {
                    System.err.println("Failed to post solution: " + e.getMessage());
                    return 1;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while posting solution.");
                    return 1;
                } catch (RuntimeException e) {
                    System.err.println("Failed to post solution: " + e.getMessage());
                    return 1;
                }

                return 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while running simulation.");
                return 1;
            } finally {
                scheduler.shutdownNow();
            }
        } finally {
            httpExecutor.shutdownNow();
        }
    }

    private static OrdersResponse fetchOrders(HttpClient http, URI ordersUri, String authToken, String authHeader, String authScheme)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(ordersUri)
                .timeout(Duration.ofSeconds(20))
                .GET();
        addAuthHeader(builder, authToken, authHeader, authScheme);
        HttpRequest request = builder.build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("GET orders failed: HTTP " + resp.statusCode());
        }

        String testId = resp.headers().firstValue("x-test-id").orElse(null);
        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode ordersNode = root.isObject() ? root.get("orders") : root;
        if (ordersNode == null || !ordersNode.isArray()) {
            throw new IllegalArgumentException("Orders payload must be an array (or {\"orders\": [...]})");
        }

        List<Order> orders = MAPPER.convertValue(ordersNode, new TypeReference<List<Map<String, Object>>>() {
        }).stream().map(Main::orderFromMap).collect(Collectors.toList());
        return new OrdersResponse(orders, testId);
    }

    private static void postSolution(
            HttpClient http,
            URI solveUri,
            List<Action> actions,
            String authToken,
            String authHeader,
            String authScheme,
            String testId
    ) throws IOException, InterruptedException {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.set("actions", MAPPER.valueToTree(actions));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(solveUri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                ;
        addAuthHeader(builder, authToken, authHeader, authScheme);
        if (testId != null && !testId.isBlank()) {
            builder.header("x-test-id", testId);
        }
        HttpRequest request = builder.build();

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
        int shelfLifeSeconds = asInt(firstNonNull(m, "shelfLifeSeconds", "shelfLife", "shelfLifeSec", "freshness"));
        Object decay = firstNonNull(m, "decayRate", "decay_rate");
        double decayRate = (decay == null) ? 1.0 : asDouble(decay);
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
        if (v.equals("ROOM") || v.equals("AMBIENT")) {
            return Order.Temperature.ROOM;
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
            v = System.getProperty(key);
        }
        if (v == null || v.isBlank()) {
            v = System.getenv(toEnvKey(key));
        }
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required arg --" + key + " (or -D" + key + "=... or $" + toEnvKey(key) + ")");
        }
        return v;
    }

    private static String optional(Map<String, String> cli, String key, String envKey) {
        return optional(cli, key, envKey, null);
    }

    private static String optional(Map<String, String> cli, String key, String envKey, String defaultValue) {
        String v = cli.get(key);
        if (v == null || v.isBlank()) {
            v = System.getProperty(key);
        }
        if ((v == null || v.isBlank()) && envKey != null && !envKey.isBlank()) {
            v = System.getenv(envKey);
        }
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v;
    }

    private static void addAuthHeader(HttpRequest.Builder builder, String authToken, String authHeader, String authScheme) {
        if (builder == null || authToken == null || authToken.isBlank()) {
            return;
        }
        String header = (authHeader == null || authHeader.isBlank()) ? "Authorization" : authHeader;
        String scheme = (authScheme == null) ? "" : authScheme.trim();
        String value = scheme.isEmpty() ? authToken : (scheme + " " + authToken);
        builder.header(header, value);
    }

    private static URI parseHttpUri(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing " + label);
        }

        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + label + " URI: " + raw);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(label + " must start with http:// or https:// (got: " + raw + ")");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException(label + " must include a hostname (example: http://localhost:8080/orders)");
        }
        return uri;
    }

    private static String toEnvKey(String key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  mvn -q exec:java -DordersUrl=https://api.cloudkitchens.com/interview/challenge/new?auth=TOKEN -DsolveUrl=https://api.cloudkitchens.com/interview/challenge/solve?auth=TOKEN");
        System.err.println("  mvn -q exec:java -Dexec.args=\"--ordersUrl https://api.cloudkitchens.com/interview/challenge/new?auth=TOKEN --solveUrl https://api.cloudkitchens.com/interview/challenge/solve?auth=TOKEN\"");
        System.err.println("GET-only (no POST):");
        System.err.println("  mvn -q exec:java -DgetOnly=true -DordersUrl=https://api.cloudkitchens.com/interview/challenge/new?auth=TOKEN");
        System.err.println("Auth (optional):");
        System.err.println("  -DauthToken=... (or $AUTH_TOKEN), -DauthHeader=Authorization, -DauthScheme=Bearer");
        System.err.println("Env vars:");
        System.err.println("  ORDERS_URL, SOLVE_URL");
    }
}
