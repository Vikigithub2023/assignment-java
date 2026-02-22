# Kitchen Simulator

Java/Maven kitchen order simulator with a simple in-memory kitchen model and an HTTP client that can fetch orders and (optionally) POST actions.

## Prerequisites

- Java 11+
- Maven 3+
- (Optional) Docker

## Build & test

```bash
cd kitchen-simulator
mvn test
```

## Run (GET-only: fetch orders)

Fetch orders and print a small summary (no POST).

```bash
cd kitchen-simulator
mvn exec:java -DgetOnly=true -DordersUrl="https://api.cloudkitchens.com/interview/challenge/new?auth=YOUR_TOKEN"
```

You can also use an environment variable:

```bash
cd kitchen-simulator
GET_ONLY=true ORDERS_URL="https://api.cloudkitchens.com/interview/challenge/new?auth=YOUR_TOKEN" mvn exec:java
```

## Run (GET + POST)

```bash
cd kitchen-simulator
mvn exec:java \
  -DordersUrl="https://api.cloudkitchens.com/interview/challenge/new?auth=YOUR_TOKEN" \
  -DsolveUrl="https://api.cloudkitchens.com/interview/challenge/solve?auth=YOUR_TOKEN"
```

Notes:
- The remote `/new` endpoint returns an `x-test-id` header. The client forwards this header to `/solve`.
- If `/solve` returns `400` with validation errors, the API likely expects additional fields/rules beyond the current implementation.

## Configuration

Required:
- `ordersUrl` (system property `-DordersUrl=...` or env `ORDERS_URL`)
- `solveUrl` (system property `-DsolveUrl=...` or env `SOLVE_URL`) for POST mode

Optional:
- `getOnly` (`-DgetOnly=true` or env `GET_ONLY=true`)
- `placeEveryMillis` (`-DplaceEveryMillis=500`)
- `awaitSeconds` (`-DawaitSeconds=60`)

Optional auth header support (not needed if you use `?auth=...` in the URL):
- `authToken` (`-DauthToken=...` or env `AUTH_TOKEN`)
- `authHeader` (`-DauthHeader=Authorization` or env `AUTH_HEADER`)
- `authScheme` (`-DauthScheme=Bearer` or env `AUTH_SCHEME`; set to empty for raw token)

## Docker

### Build image

```bash
cd kitchen-simulator
docker build -t kitchen-simulator:local .
```

### Run (GET-only)

```bash
docker run --rm \
  -e GET_ONLY=true \
  -e ORDERS_URL="https://api.cloudkitchens.com/interview/challenge/new?auth=YOUR_TOKEN" \
  kitchen-simulator:local
```

### Run (GET + POST)

```bash
docker run --rm \
  -e ORDERS_URL="https://api.cloudkitchens.com/interview/challenge/new?auth=YOUR_TOKEN" \
  -e SOLVE_URL="https://api.cloudkitchens.com/interview/challenge/solve?auth=YOUR_TOKEN" \
  kitchen-simulator:local
```

