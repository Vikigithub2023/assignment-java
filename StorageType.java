public enum StorageType {
    HEATER,
    COOLER,
    FREEZER,
    SHELF;

    public boolean isIdealFor(Order order) {
        if (order == null) {
            return false;
        }

        return switch (this) {
            case HEATER -> order.getTemperature() == Order.Temperature.HOT;
            case COOLER -> order.getTemperature() == Order.Temperature.COLD;
            case FREEZER -> order.getTemperature() == Order.Temperature.FROZEN;
            case SHELF -> false;
        };
    }
}
