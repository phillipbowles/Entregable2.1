package game;

public class GameConfig {

    // Tablero
    public static final int BOARD_SIZE = 10;

    // Jugadores
    public static final int MIN_PLAYERS = 3;
    public static final int INITIAL_LIVES = 2;
    public static final int PLAYER_SLEEP_MIN = 1000;
    public static final int PLAYER_SLEEP_MAX = 3000;

    // Tiempo de juego
    public static final int GAME_TIME_LIMIT = 60000;

    // Robot de vidas
    public static final int MAX_LIVES = 5;
    public static final int LIFE_SLEEP_MIN = 2000;
    public static final int LIFE_SLEEP_MAX = 4000;

    // Robot de monedas
    public static final int COIN_SLEEP_MIN = 1500;
    public static final int COIN_SLEEP_MAX = 3500;
    public static final int[] COIN_VALUES = {1, 2, 5, 10};

    // Robot de trampas
    public static final int TRAP_SLEEP_MIN = 2500;
    public static final int TRAP_SLEEP_MAX = 5000;

    // Otros
    public static final String LOG_FILE = "game.log";

    public static int maxCoinCells() {
        return (int) (BOARD_SIZE * BOARD_SIZE * 0.1);
    }

    public static int maxTraps() {
        return (int) (BOARD_SIZE * BOARD_SIZE * 0.1);
    }

    public static void print() {
        System.out.println("Config: Board " + BOARD_SIZE + "x" + BOARD_SIZE +
                ", Min players " + MIN_PLAYERS +
                ", Game time " + (GAME_TIME_LIMIT/1000) + "s");
    }
}