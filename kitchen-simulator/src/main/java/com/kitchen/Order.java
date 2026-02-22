package com.kitchen;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class Order {
    public enum Temperature {
        HOT,
        COLD,
        FROZEN
    }

    private final String id;
    private final String name;
    private final Temperature temperature;
    private final int shelfLifeSeconds;
    private final double decayRate;

    @JsonCreator
    public Order(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("temp") Temperature temperature,
            @JsonProperty("shelfLife") int shelfLifeSeconds,
            @JsonProperty("decayRate") double decayRate
    ) {
        // Some payloads use "temperature"/"shelfLifeSeconds" instead; Main handles those cases.
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.temperature = Objects.requireNonNull(temperature, "temperature");
        this.shelfLifeSeconds = shelfLifeSeconds;
        this.decayRate = decayRate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Temperature getTemperature() {
        return temperature;
    }

    public int getShelfLifeSeconds() {
        return shelfLifeSeconds;
    }

    public double getDecayRate() {
        return decayRate;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", temperature=" + temperature +
                ", shelfLifeSeconds=" + shelfLifeSeconds +
                ", decayRate=" + decayRate +
                '}';
    }
}

