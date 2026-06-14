package ar.vger32app.ui.auth;

/*
 * Which screen AuthActivity shows on launch:
 * SET_CODE when no PIN exists yet, REQ_CODE when the user must authenticate.
 */

public enum StartDestination {
    SET_CODE(3),
    REQ_CODE(4);

    private final int value;

    StartDestination(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static StartDestination fromValue(int value) {
        for (StartDestination destination : StartDestination.values()) {
            if (destination.getValue() == value) {
                return destination;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }
}
