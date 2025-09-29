package board;

import entities.Player;
import game.Display;
import game.Logger;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

public class Board {
    private int size;
    private Cell[][] grid;
    private ReentrantReadWriteLock lock;
    private Random random;
    private Display display;
    private Logger logger;

    // Contadores
    private AtomicInteger livesCount;
    private AtomicInteger coinsCount;
    private AtomicInteger trapsCount;
    private AtomicInteger coinCells;

    // Semáforos para cada casilla
    private Semaphore[][] cellLocks;

    public Board(int size) {
        this.size = size;
        this.lock = new ReentrantReadWriteLock();
        this.random = new Random();
        this.livesCount = new AtomicInteger(0);
        this.coinsCount = new AtomicInteger(0);
        this.trapsCount = new AtomicInteger(0);
        this.coinCells = new AtomicInteger(0);

        // Inicializar grid
        this.grid = new Cell[size][size];
        this.cellLocks = new Semaphore[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                grid[i][j] = new Cell();
                cellLocks[i][j] = new Semaphore(1);
            }
        }
    }

    public void setDisplay(Display display) {
        this.display = display;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    // Clase Cell simplificada
    public static class Cell {
        private Object occupant;
        private ItemType item;
        private int quantity;

        public Cell() {
            this.occupant = null;
            this.item = null;
            this.quantity = 0;
        }

        public synchronized boolean isEmpty() {
            return occupant == null;
        }

        public synchronized boolean hasItem() {
            return item != null;
        }

        public synchronized Object getOccupant() { return occupant; }
        public synchronized void setOccupant(Object occ) { this.occupant = occ; }
        public synchronized ItemType getItem() { return item; }
        public synchronized int getQuantity() { return quantity; }
        public synchronized void setItem(ItemType type, int qty) {
            this.item = type;
            this.quantity = qty;
        }
        public synchronized void clearItem() {
            this.item = null;
            this.quantity = 0;
        }
    }

    public enum ItemType {
        COIN, LIFE, TRAP
    }

    public static class Position {
        private int row, col;

        public Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        public int getRow() { return row; }
        public int getCol() { return col; }

        @Override
        public String toString() {
            return "(" + row + "," + col + ")";
        }
    }

    // Métodos para jugadores
    public Position getRandomFreePosition() {
        List<Position> free = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (grid[i][j].isEmpty()) {
                        free.add(new Position(i, j));
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        if (free.isEmpty()) return null;
        return free.get(random.nextInt(free.size()));
    }

    public boolean tryOccupyCell(int row, int col, Object occupant) {
        if (!isValid(row, col)) return false;

        if (!cellLocks[row][col].tryAcquire()) {
            return false; // Ya ocupada
        }

        if (!grid[row][col].isEmpty()) {
            cellLocks[row][col].release();
            return false;
        }

        grid[row][col].setOccupant(occupant);

        if (display != null) {
            display.updateCell(row, col, occupant, grid[row][col].getItem(), grid[row][col].getQuantity());
        }

        log("Cell " + row + "," + col + " occupied");
        return true;
    }

    public void releaseCell(int row, int col, Object occupant) {
        if (!isValid(row, col)) return;

        if (grid[row][col].getOccupant() == occupant) {
            grid[row][col].setOccupant(null);

            if (display != null) {
                display.updateCell(row, col, null, grid[row][col].getItem(), grid[row][col].getQuantity());
            }

            log("Cell " + row + "," + col + " released");
        }

        cellLocks[row][col].release();
    }

    public ItemType collectItem(int row, int col) {
        if (!isValid(row, col)) return null;

        Cell cell = grid[row][col];
        if (!cell.hasItem()) return null;

        ItemType type = cell.getItem();
        int qty = cell.getQuantity();

        // Actualizar contadores
        if (type == ItemType.COIN) {
            coinsCount.addAndGet(-qty);
            coinCells.decrementAndGet();
        } else if (type == ItemType.LIFE) {
            livesCount.decrementAndGet();
        }

        cell.clearItem();

        if (display != null) {
            display.updateCell(row, col, cell.getOccupant(), null, 0);
        }

        log("Collected " + type + " (" + qty + ") at " + row + "," + col);
        return type;
    }

    public boolean isCellOccupied(int row, int col) {
        if (!isValid(row, col)) return true;
        return !grid[row][col].isEmpty();
    }

    public ItemType getItemType(int row, int col) {
        if (!isValid(row, col)) return null;
        return grid[row][col].getItem();
    }

    public int getItemQuantity(int row, int col) {
        if (!isValid(row, col)) return 0;
        return grid[row][col].getQuantity();
    }

    // Métodos para robots
    public boolean placeLife() {
        Position pos = getRandomFreeSpot();
        if (pos == null) return false;

        grid[pos.getRow()][pos.getCol()].setItem(ItemType.LIFE, 1);
        livesCount.incrementAndGet();

        if (display != null) {
            display.updateCell(pos.getRow(), pos.getCol(), null, ItemType.LIFE, 1);
        }

        log("Life placed at " + pos);
        return true;
    }

    public boolean placeCoins() {
        // Máximo 10% de casillas con monedas
        if (coinCells.get() >= size * size * 0.1) {
            return false;
        }

        Position pos = getRandomFreeSpot();
        if (pos == null) return false;

        int[] amounts = {1, 2, 5, 10};
        int coins = amounts[random.nextInt(amounts.length)];

        grid[pos.getRow()][pos.getCol()].setItem(ItemType.COIN, coins);
        coinsCount.addAndGet(coins);
        coinCells.incrementAndGet();

        if (display != null) {
            display.updateCell(pos.getRow(), pos.getCol(), null, ItemType.COIN, coins);
        }

        log("Coins (" + coins + ") placed at " + pos);
        return true;
    }

    public boolean placeTrap() {
        // Máximo 10% de casillas con trampas
        if (trapsCount.get() >= size * size * 0.1) {
            return false;
        }

        Position pos = getRandomFreeSpot();
        if (pos == null) return false;

        grid[pos.getRow()][pos.getCol()].setItem(ItemType.TRAP, 1);
        trapsCount.incrementAndGet();

        if (display != null) {
            display.updateCell(pos.getRow(), pos.getCol(), null, ItemType.TRAP, 1);
        }

        log("Trap placed at " + pos);
        return true;
    }

    private Position getRandomFreeSpot() {
        List<Position> free = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (grid[i][j].isEmpty() && !grid[i][j].hasItem()) {
                        free.add(new Position(i, j));
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        if (free.isEmpty()) return null;
        return free.get(random.nextInt(free.size()));
    }

    // Métodos de utilidad
    public boolean isValid(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public int getSize() { return size; }
    public int getLivesCount() { return livesCount.get(); }
    public int getCoinsCount() { return coinsCount.get(); }
    public int getTrapsCount() { return trapsCount.get(); }
    public int getCoinCells() { return coinCells.get(); }

    public boolean canPlaceCoins() {
        return coinCells.get() < size * size * 0.1;
    }

    public boolean canPlaceTraps() {
        return trapsCount.get() < size * size * 0.1;
    }

    private void log(String msg) {
        if (logger != null) {
            logger.log(msg);
        }
    }

    // Debug
    public String printBoard() {
        StringBuilder sb = new StringBuilder();
        lock.readLock().lock();
        try {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    Cell cell = grid[i][j];
                    if (cell.getOccupant() != null) {
                        // Si el ocupante es un Player, usar su emoji específico
                        if (cell.getOccupant() instanceof Player) {
                            Player player = (Player) cell.getOccupant();
                            sb.append(player.getPlayerEmoji()).append(" ");
                        } else {
                            sb.append("👤 ");  // Fallback genérico
                        }
                    } else if (cell.hasItem()) {
                        switch (cell.getItem()) {
                            case COIN:
                                sb.append("🟡 ");  // Moneda amarilla
                                break;
                            case LIFE:
                                sb.append("🍏 ");  // Vida como manzana
                                break;
                            case TRAP:
                                sb.append("❌️ ");  // Trampa de muerte
                                break;
                        }
                    } else {
                        sb.append("🔲 ");  // Casilla vacía
                    }
                }
                sb.append("\n");
            }
        } finally {
            lock.readLock().unlock();
        }
        return sb.toString();
    }
}