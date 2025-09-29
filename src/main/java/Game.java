
import board.Board;
import entities.CoinRobot;
import entities.LifeRobot;
import entities.Player;
import entities.TrapRobot;
import game.Display;
import game.GameConfig;
import game.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game {
    private Board board;
    private Display display;
    private Logger logger;
    private List<Player> allPlayers;        // Todos los jugadores registrados
    private List<Player> activePlayers;     // Jugadores de la partida actual
    private List<Player> waitingPlayers;    // Jugadores esperando
    private List<Thread> playerThreads;
    private CountDownLatch startLatch;
    private AtomicBoolean gameActive;
    private AtomicBoolean gameEnded;
    private int gameNumber;

    // Robots
    private LifeRobot lifeRobot;
    private CoinRobot coinRobot;
    private TrapRobot trapRobot;
    private Thread lifeThread;
    private Thread coinThread;
    private Thread trapThread;
    private Thread displayThread;
    private Thread loggerThread;

    public Game() {
        this.allPlayers = new ArrayList<>();
        this.activePlayers = new ArrayList<>();
        this.waitingPlayers = new ArrayList<>();
        this.playerThreads = new ArrayList<>();
        this.gameActive = new AtomicBoolean(false);
        this.gameEnded = new AtomicBoolean(false);
        this.gameNumber = 1;

        initializeComponents();
    }

    private void initializeComponents() {
        this.board = new Board(GameConfig.BOARD_SIZE);
        this.logger = new Logger(GameConfig.LOG_FILE);
        this.display = new Display(board);

        // Conectar componentes
        board.setDisplay(display);
        board.setLogger(logger);
        display.setLogger(logger);

        // Crear robots
        this.lifeRobot = new LifeRobot(board);
        this.coinRobot = new CoinRobot(board);
        this.trapRobot = new TrapRobot(board);

        lifeRobot.setLogger(logger);
        coinRobot.setLogger(logger);
        trapRobot.setLogger(logger);

        // Configurar robots
        lifeRobot.setConfig(GameConfig.MAX_LIVES, GameConfig.LIFE_SLEEP_MIN, GameConfig.LIFE_SLEEP_MAX);
        coinRobot.setConfig(GameConfig.COIN_SLEEP_MIN, GameConfig.COIN_SLEEP_MAX);
        trapRobot.setConfig(GameConfig.TRAP_SLEEP_MIN, GameConfig.TRAP_SLEEP_MAX);
    }

    public static void main(String[] args) {
        Game game = new Game();
        game.run();
    }

    public void run() {
        System.out.println("=== CONCURRENT BOARD GAME ===");
        GameConfig.print();

        // Iniciar sistema
        startSystemThreads();

        // Registrar todos los jugadores al inicio
        registerAllPlayers();

        // Loop de partidas
        while (hasEnoughPlayersForGame()) {
            prepareNextGame();
            startGame();
            monitorGame();
            endGame();

            if (!askForNextGame()) {
                break;
            }
        }

        shutdownSystem();
        System.out.println("Simulation finished!");
    }

    private void startSystemThreads() {
        logger.start();
        loggerThread = new Thread(logger);
        loggerThread.start();

        display.start();
        displayThread = new Thread(display);
        displayThread.start();
    }

    private void registerAllPlayers() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== PLAYER REGISTRATION ===");
        System.out.println("Minimum players per game: " + GameConfig.MIN_PLAYERS);
        System.out.println("Additional players must be in groups of " + GameConfig.MIN_PLAYERS);
        System.out.println("(3, 6, 9, 12... players total)");

        while (true) {
            System.out.println("\nCurrent registered players: " + allPlayers.size());

            if (allPlayers.size() > 0) {
                int gamesReady = allPlayers.size() / GameConfig.MIN_PLAYERS;
                int playersWaiting = allPlayers.size() % GameConfig.MIN_PLAYERS;
                System.out.println("Games ready: " + gamesReady);
                if (playersWaiting > 0) {
                    System.out.println("Players waiting for next group: " + playersWaiting +
                            " (need " + (GameConfig.MIN_PLAYERS - playersWaiting) + " more)");
                }
            }

            System.out.print("Add player? (y/n): ");
            String input = scanner.nextLine();

            if (input.toLowerCase().equals("y")) {
                addPlayerToRegistry();
            } else if (input.toLowerCase().equals("n")) {
                if (allPlayers.size() >= GameConfig.MIN_PLAYERS) {
                    break;
                } else {
                    System.out.println("Need at least " + GameConfig.MIN_PLAYERS + " players to start!");
                }
            }
        }

        int totalGames = allPlayers.size() / GameConfig.MIN_PLAYERS;
        int leftover = allPlayers.size() % GameConfig.MIN_PLAYERS;

        System.out.println("\n=== REGISTRATION COMPLETE ===");
        System.out.println("Total players: " + allPlayers.size());
        System.out.println("Games possible: " + totalGames);
        if (leftover > 0) {
            System.out.println("Players that won't play: " + leftover + " (need groups of " + GameConfig.MIN_PLAYERS + ")");
        }
    }

    private void addPlayerToRegistry() {
        int playerId = allPlayers.size() + 1;
        Player player = new Player(playerId, board);
        player.setLogger(logger);
        player.setSleepTime(GameConfig.PLAYER_SLEEP_MIN, GameConfig.PLAYER_SLEEP_MAX);

        allPlayers.add(player);
        System.out.println("Player " + playerId + " " + player.getPlayerEmoji() + " registered");
    }

    private boolean hasEnoughPlayersForGame() {
        int playersLeft = allPlayers.size() - ((gameNumber - 1) * GameConfig.MIN_PLAYERS);
        return playersLeft >= GameConfig.MIN_PLAYERS;
    }

    private void prepareNextGame() {
        // Limpiar estado anterior
        activePlayers.clear();
        playerThreads.clear();
        waitingPlayers.clear();
        gameActive.set(false);
        gameEnded.set(false);

        // Reiniciar componentes para nueva partida
        if (gameNumber > 1) {
            initializeComponents();
        }

        // Seleccionar jugadores para esta partida
        int startIndex = (gameNumber - 1) * GameConfig.MIN_PLAYERS;
        for (int i = startIndex; i < startIndex + GameConfig.MIN_PLAYERS && i < allPlayers.size(); i++) {
            Player player = allPlayers.get(i);
            // Recrear player con mismo ID y emoji pero estado limpio
            Player gamePlayer = new Player(player.getId(), board);
            gamePlayer.setLogger(logger);
            gamePlayer.setSleepTime(GameConfig.PLAYER_SLEEP_MIN, GameConfig.PLAYER_SLEEP_MAX);
            activePlayers.add(gamePlayer);
        }

        // Calcular jugadores esperando
        int totalPlayersUsed = gameNumber * GameConfig.MIN_PLAYERS;
        for (int i = totalPlayersUsed; i < allPlayers.size(); i++) {
            waitingPlayers.add(allPlayers.get(i));
        }

        System.out.println("\n=== GAME " + gameNumber + " ===");
        System.out.print("Players: ");
        for (Player p : activePlayers) {
            System.out.print(p.getId() + " " + p.getPlayerEmoji() + " ");
        }
        System.out.println();
        if (!waitingPlayers.isEmpty()) {
            System.out.println("Waiting players: " + waitingPlayers.size());
        }
    }

    private void startGame() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Press Enter to start Game " + gameNumber + "...");
        scanner.nextLine();

        startLatch = new CountDownLatch(1);
        gameActive.set(true);

        // Crear hilos de jugadores
        for (Player player : activePlayers) {
            Thread thread = new Thread(player);
            playerThreads.add(thread);
            thread.start();
        }

        // Iniciar robots
        lifeThread = new Thread(lifeRobot);
        coinThread = new Thread(coinRobot);
        trapThread = new Thread(trapRobot);

        lifeRobot.startGame();
        coinRobot.startGame();
        trapRobot.startGame();

        lifeThread.start();
        coinThread.start();
        trapThread.start();

        // Iniciar jugadores
        for (Player player : activePlayers) {
            player.startGame();
        }

        logger.logGameStart(GameConfig.BOARD_SIZE, activePlayers.size());
        display.showGameStart();

        System.out.println("Game " + gameNumber + " started!");
    }

    private void monitorGame() {
        long gameStartTime = System.currentTimeMillis();

        while (gameActive.get()) {
            try {
                Thread.sleep(1000);

                // Verificar tiempo límite
                long elapsed = System.currentTimeMillis() - gameStartTime;
                if (elapsed >= GameConfig.GAME_TIME_LIMIT) {
                    logger.log("Time limit reached for Game " + gameNumber);
                    break;
                }

                // Verificar si solo queda un jugador vivo
                int alivePlayers = 0;
                for (Player p : activePlayers) {
                    if (p.isAlive()) alivePlayers++;
                }

                if (alivePlayers <= 1) {
                    logger.log("Only one player remaining in Game " + gameNumber);
                    break;
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void endGame() {
        gameActive.set(false);
        gameEnded.set(true);

        // Detener jugadores
        for (Player player : activePlayers) {
            player.stopGame();
        }

        // Detener robots
        lifeRobot.stopGame();
        coinRobot.stopGame();
        trapRobot.stopGame();

        // Esperar que terminen los hilos
        waitForThreads();

        // Mostrar resultados
        showResults();

        gameNumber++;
    }

    private void waitForThreads() {
        try {
            for (Thread thread : playerThreads) {
                thread.join(1000);
            }

            lifeThread.join(1000);
            coinThread.join(1000);
            trapThread.join(1000);

        } catch (InterruptedException e) {
            // Ignorar
        }
    }

    private void showResults() {
        // Encontrar ganador
        Player winner = null;
        int maxCoins = -1;

        for (Player p : activePlayers) {
            if (p.isAlive() && p.getCoins() > maxCoins) {
                maxCoins = p.getCoins();
                winner = p;
            }
        }

        // Mostrar resultados
        System.out.println("\n=== GAME " + (gameNumber) + " RESULTS ===");
        display.showResults(activePlayers);
        logger.logResults(activePlayers);

        if (winner != null) {
            display.showWinner(winner);
            logger.logWinner(winner);
        } else {
            System.out.println("No winner - all players died!");
            logger.log("No winner - all players died in Game " + gameNumber);
        }

        display.showGameEnd();
        logger.logGameEnd("Game " + gameNumber + " completed");
    }

    private boolean askForNextGame() {
        Scanner scanner = new Scanner(System.in);

        int playersUsed = (gameNumber - 1) * GameConfig.MIN_PLAYERS; // Usar gameNumber-1 porque ya se incrementó
        int playersLeft = allPlayers.size() - playersUsed;

        System.out.println("\n=== NEXT GAME OPTION ===");
        System.out.println("Players waiting: " + playersLeft);

        if (playersLeft >= GameConfig.MIN_PLAYERS) {
            // Hay suficientes jugadores para otra partida completa
            int gamesRemaining = playersLeft / GameConfig.MIN_PLAYERS;
            System.out.println("Games remaining possible: " + gamesRemaining);
            System.out.print("Start next game? (y/n): ");
            String input = scanner.nextLine();
            return input.toLowerCase().equals("y");
        } else if (playersLeft > 0) {
            // Hay jugadores esperando pero no suficientes para una partida completa
            int needed = GameConfig.MIN_PLAYERS - playersLeft;
            System.out.println("Need " + needed + " more players for another complete game.");
            System.out.print("Add " + needed + " more players for next game? (y/n): ");
            String input = scanner.nextLine();

            if (input.toLowerCase().equals("y")) {
                // Agregar los jugadores que faltan
                for (int i = 0; i < needed; i++) {
                    addPlayerToRegistry();
                }
                return true;
            } else {
                System.out.println("Ending simulation. " + playersLeft + " player(s) did not get to play.");
                return false;
            }
        } else {
            // No hay más jugadores
            System.out.println("All players have played. Simulation complete!");
            return false;
        }
    }

    private void shutdownSystem() {
        display.stop();
        logger.stop();

        try {
            displayThread.join(2000);
            loggerThread.join(2000);
        } catch (InterruptedException e) {
            // Ignorar
        }
    }
}