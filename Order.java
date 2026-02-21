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

    public Order(String id, String name, Temperature temperature, int shelfLifeSeconds, double decayRate) {
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
