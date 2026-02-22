public enum StorageType {
    HEATER,
    COOLER,
    FREEZER,
    SHELF;

    public boolean isIdealFor(Order order) {
        if (order == null) {
            return false;
        }

        switch (this) {
            case HEATER:
                return order.getTemperature() == Order.Temperature.HOT;
            case COOLER:
                return order.getTemperature() == Order.Temperature.COLD;
            case FREEZER:
                return order.getTemperature() == Order.Temperature.FROZEN;
            case SHELF:
            default:
                return false;
        }
    }
}
