package entities;

import board.Board;
import game.Logger;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

public class Player implements Runnable {
    private int playerId;
    private Board board;
    private Random random;
    private Logger logger;
    private String playerEmoji;

    // Lista de emojis de personas
    private static final String[] PLAYER_EMOJIS = {
            "🧞‍♂️", "🧚‍♂️", "🧜", "🧌",
            "👴", "👵", "🧒", "👦", "👧", "🧑",
            "👨‍💼", "👩‍💼"
    };
    // Estado del jugador
    private AtomicInteger lives;
    private AtomicInteger coins;
    private Board.Position currentPosition;
    private AtomicBoolean isAlive;
    private AtomicBoolean gameActive;

    // Configuración
    private int minSleep = 1000;
    private int maxSleep = 3000;

    public Player(int id, Board board) {
        this.playerId = id;
        this.board = board;
        this.random = new Random();
        this.lives = new AtomicInteger(2);
        this.coins = new AtomicInteger(0);
        this.isAlive = new AtomicBoolean(true);
        this.gameActive = new AtomicBoolean(false);

        // Asignar emoji único basado en ID
        this.playerEmoji = PLAYER_EMOJIS[(id - 1) % PLAYER_EMOJIS.length];
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setSleepTime(int min, int max) {
        this.minSleep = min;
        this.maxSleep = max;
    }

    public void startGame() {
        gameActive.set(true);
    }

    public void stopGame() {
        gameActive.set(false);
    }

    @Override
    public void run() {
        // Esperar que empiece el juego
        while (!gameActive.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }

        // Buscar posición inicial
        if (!findStartPosition()) {
            log("Player " + playerId + " " + playerEmoji + " couldn't find start position");
            return;
        }

        log("Player " + playerId + " " + playerEmoji + " started at " + currentPosition);

        // Loop principal
        while (isAlive.get() && gameActive.get()) {
            try {
                playTurn();
                sleep();
            } catch (InterruptedException e) {
                break;
            }
        }

        // Limpiar al salir
        if (currentPosition != null) {
            board.releaseCell(currentPosition.getRow(), currentPosition.getCol(), this);
        }
        log("Player " + playerId + " " + playerEmoji + " finished - Lives: " + lives.get() + " Coins: " + coins.get());
    }

    private boolean findStartPosition() {
        for (int i = 0; i < 5; i++) {
            Board.Position pos = board.getRandomFreePosition();
            if (pos != null && board.tryOccupyCell(pos.getRow(), pos.getCol(), this)) {
                currentPosition = pos;
                return true;
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    private void playTurn() {
        // Tirar dado
        int dice = random.nextInt(6) + 1;
        log("Player " + playerId + " " + playerEmoji + " rolled " + dice);

        // Buscar items cercanos y planificar movimiento
        List<Board.Position> path = findPathToNearestItem(dice);

        if (path.isEmpty()) {
            log("Player " + playerId + " " + playerEmoji + " has no moves towards items");
            return;
        }

        // Moverse hacia el item
        moveAlongPath(path, dice);
    }

    /**
     * Encuentra el camino hacia el item más cercano (moneda o vida)
     */
    private List<Board.Position> findPathToNearestItem(int steps) {
        // Buscar todos los items de interés en el tablero
        List<ItemTarget> targets = findNearestItems();

        if (targets.isEmpty()) {
            // Si no hay items, usar el movimiento original
            return findRandomPath(steps);
        }

        // Intentar llegar a cada target en orden de distancia
        for (ItemTarget target : targets) {
            List<Board.Position> path = planPathToTarget(target.position, steps);
            if (!path.isEmpty()) {
                log("Player " + playerId + " " + playerEmoji + " targeting " +
                        target.itemType + " at " + target.position + " (distance: " + target.distance + ")");
                return path;
            }
        }

        // Si no puede llegar completamente a ningún item, moverse en dirección del más cercano
        if (!targets.isEmpty()) {
            ItemTarget closestTarget = targets.get(0); // Ya están ordenados por distancia
            List<Board.Position> partialPath = moveTowardsTarget(closestTarget.position, steps);
            if (!partialPath.isEmpty()) {
                log("Player " + playerId + " " + playerEmoji + " moving towards " +
                        closestTarget.itemType + " at " + closestTarget.position + " (getting closer)");
                return partialPath;
            }
        }

        // Si tampoco puede moverse hacia ningún item, movimiento aleatorio
        return findRandomPath(steps);
    }

    /**
     * Encuentra todos los items cercanos ordenados por distancia
     */
    private List<ItemTarget> findNearestItems() {
        List<ItemTarget> targets = new ArrayList<>();
        int boardSize = board.getSize();

        // Escanear todo el tablero buscando monedas y vidas
        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                Board.ItemType item = board.getItemType(row, col);
                if (item == Board.ItemType.COIN) {
                    int distance = Math.abs(row - currentPosition.getRow()) +
                            Math.abs(col - currentPosition.getCol());
                    targets.add(new ItemTarget(new Board.Position(row, col), item, distance));
                }
            }
        }

        // Ordenar por distancia (más cercano primero)
        targets.sort((a, b) -> Integer.compare(a.distance, b.distance));
        return targets;
    }

    /**
     * Planifica un camino hacia un target específico
     */
    private List<Board.Position> planPathToTarget(Board.Position target, int maxSteps) {
        int currentRow = currentPosition.getRow();
        int currentCol = currentPosition.getCol();
        int targetRow = target.getRow();
        int targetCol = target.getCol();

        // Calcular dirección óptima
        int rowDiff = targetRow - currentRow;
        int colDiff = targetCol - currentCol;

        // Priorizar la dirección con mayor diferencia
        List<int[]> directions = new ArrayList<>();

        if (Math.abs(rowDiff) >= Math.abs(colDiff)) {
            // Priorizar movimiento vertical
            if (rowDiff > 0) directions.add(new int[]{1, 0});   // Abajo
            if (rowDiff < 0) directions.add(new int[]{-1, 0});  // Arriba
            if (colDiff > 0) directions.add(new int[]{0, 1});   // Derecha
            if (colDiff < 0) directions.add(new int[]{0, -1});  // Izquierda
        } else {
            // Priorizar movimiento horizontal
            if (colDiff > 0) directions.add(new int[]{0, 1});   // Derecha
            if (colDiff < 0) directions.add(new int[]{0, -1});  // Izquierda
            if (rowDiff > 0) directions.add(new int[]{1, 0});   // Abajo
            if (rowDiff < 0) directions.add(new int[]{-1, 0});  // Arriba
        }

        // Intentar cada dirección
        for (int[] dir : directions) {
            List<Board.Position> path = buildPath(currentRow, currentCol, dir, maxSteps);
            if (!path.isEmpty()) {
                return path;
            }
        }

        return new ArrayList<>();
    }

    /**
     * Se mueve hacia un target usando todos los pasos disponibles,
     * incluso si no puede llegar completamente
     */
    private List<Board.Position> moveTowardsTarget(Board.Position target, int maxSteps) {
        int currentRow = currentPosition.getRow();
        int currentCol = currentPosition.getCol();
        int targetRow = target.getRow();
        int targetCol = target.getCol();

        // Calcular diferencias
        int rowDiff = targetRow - currentRow;
        int colDiff = targetCol - currentCol;

        // Determinar las direcciones óptimas ordenadas por prioridad
        List<DirectionInfo> directions = new ArrayList<>();

        // Agregar direcciones con su beneficio (qué tanto nos acerca al objetivo)
        if (rowDiff > 0) directions.add(new DirectionInfo(new int[]{1, 0}, Math.abs(rowDiff), "down"));
        if (rowDiff < 0) directions.add(new DirectionInfo(new int[]{-1, 0}, Math.abs(rowDiff), "up"));
        if (colDiff > 0) directions.add(new DirectionInfo(new int[]{0, 1}, Math.abs(colDiff), "right"));
        if (colDiff < 0) directions.add(new DirectionInfo(new int[]{0, -1}, Math.abs(colDiff), "left"));

        // Ordenar por beneficio (mayor beneficio primero)
        directions.sort((a, b) -> Integer.compare(b.benefit, a.benefit));

        // Intentar cada dirección y usar el máximo de pasos posible
        for (DirectionInfo dirInfo : directions) {
            List<Board.Position> path = buildMaxPath(currentRow, currentCol, dirInfo.direction, maxSteps);
            if (!path.isEmpty()) {
                log("Player " + playerId + " " + playerEmoji + " moving " + dirInfo.name +
                        " towards target (can move " + path.size() + "/" + maxSteps + " steps)");
                return path;
            }
        }

        return new ArrayList<>();
    }

    /**
     * Construye el camino más largo posible en una dirección
     */
    private List<Board.Position> buildMaxPath(int startRow, int startCol, int[] direction, int maxSteps) {
        List<Board.Position> path = new ArrayList<>();

        for (int step = 1; step <= maxSteps; step++) {
            int newRow = startRow + direction[0] * step;
            int newCol = startCol + direction[1] * step;

            if (!board.isValid(newRow, newCol) || board.isCellOccupied(newRow, newCol)) {
                break;
            }

            path.add(new Board.Position(newRow, newCol));
        }

        return path;
    }

    /**
     * Construye un camino en una dirección específica
     */
    private List<Board.Position> buildPath(int startRow, int startCol, int[] direction, int maxSteps) {
        List<Board.Position> path = new ArrayList<>();

        for (int step = 1; step <= maxSteps; step++) {
            int newRow = startRow + direction[0] * step;
            int newCol = startCol + direction[1] * step;

            if (!board.isValid(newRow, newCol) || board.isCellOccupied(newRow, newCol)) {
                break;
            }

            path.add(new Board.Position(newRow, newCol));
        }

        return path;
    }

    /**
     * Movimiento aleatorio original (fallback)
     */
    private List<Board.Position> findRandomPath(int steps) {
        // Direcciones: arriba, abajo, derecha, izquierda
        int[][] dirs = {{-1,0}, {1,0}, {0,1}, {0,-1}};

        for (int[] dir : dirs) {
            List<Board.Position> path = buildPath(currentPosition.getRow(), currentPosition.getCol(), dir, steps);
            if (!path.isEmpty()) {
                return path;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Mueve al jugador a lo largo del camino, verificando en cada paso
     */
    private void moveAlongPath(List<Board.Position> plannedPath, int diceRoll) {
        int stepsMoved = 0;

        for (Board.Position nextPos : plannedPath) {
            if (!gameActive.get() || !isAlive.get()) break;

            // Verificar si la casilla sigue libre antes de moverse
            if (board.isCellOccupied(nextPos.getRow(), nextPos.getCol())) {
                log("Player " + playerId + " " + playerEmoji + " path blocked at " + nextPos + ", replanning...");

                // Replanificar desde la posición actual con los pasos restantes
                int remainingSteps = diceRoll - stepsMoved;
                if (remainingSteps > 0) {
                    List<Board.Position> newPath = findPathToNearestItem(remainingSteps);
                    if (!newPath.isEmpty()) {
                        moveAlongPath(newPath, remainingSteps);
                    }
                }
                break;
            }

            // Liberar posición actual
            board.releaseCell(currentPosition.getRow(), currentPosition.getCol(), this);

            // Ocupar nueva posición
            if (board.tryOccupyCell(nextPos.getRow(), nextPos.getCol(), this)) {
                currentPosition = nextPos;
                stepsMoved++;
                handleItem();
                log("Player " + playerId + " " + playerEmoji + " moved to " + nextPos + " (step " + stepsMoved + ")");

                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            } else {
                // Si no pudo, volver a la anterior
                board.tryOccupyCell(currentPosition.getRow(), currentPosition.getCol(), this);
                break;
            }
        }
    }

    private void handleItem() {
        Board.ItemType item = board.getItemType(currentPosition.getRow(), currentPosition.getCol());
        if (item == null) return;

        int quantity = board.getItemQuantity(currentPosition.getRow(), currentPosition.getCol());
        board.collectItem(currentPosition.getRow(), currentPosition.getCol());

        switch (item) {
            case COIN:
                coins.addAndGet(quantity);
                log("Player " + playerId + " " + playerEmoji + " got " + quantity + " coins (total: " + coins.get() + ")");
                break;
            case LIFE:
                lives.incrementAndGet();
                log("Player " + playerId + " " + playerEmoji + " got life (total: " + lives.get() + ")");
                break;
            case TRAP:
                lives.decrementAndGet();
                log("Player " + playerId + " " + playerEmoji + " hit trap! (lives: " + lives.get() + ")");
                if (lives.get() <= 0) {
                    isAlive.set(false);
                    log("Player " + playerId + " " + playerEmoji + " died!");
                }
                break;
        }
    }

    private void sleep() throws InterruptedException {
        int time = random.nextInt(maxSleep - minSleep + 1) + minSleep;
        Thread.sleep(time);
    }

    private void log(String msg) {
        if (logger != null) {
            logger.log(msg);
        }
    }

    // Getters
    public int getId() { return playerId; }
    public int getLives() { return lives.get(); }
    public int getCoins() { return coins.get(); }
    public boolean isAlive() { return isAlive.get(); }
    public Board.Position getPosition() { return currentPosition; }
    public String getPlayerEmoji() { return playerEmoji; }

    @Override
    public String toString() {
        return "Player " + playerId + " " + playerEmoji + " [Lives:" + lives.get() +
                " Coins:" + coins.get() + " Alive:" + isAlive.get() + "]";
    }

    /**
     * Clase auxiliar para representar targets de items
     */
    private static class ItemTarget {
        Board.Position position;
        Board.ItemType itemType;
        int distance;

        ItemTarget(Board.Position position, Board.ItemType itemType, int distance) {
            this.position = position;
            this.itemType = itemType;
            this.distance = distance;
        }
    }

    /**
     * Clase auxiliar para dirección y beneficio de avanzar hacia un ítem
     */
    private static class DirectionInfo {
        int[] direction;
        int benefit;
        String name;
        DirectionInfo(int[] direction, int benefit, String name) {
            this.direction = direction;
            this.benefit = benefit;
            this.name = name;
        }
    }
}