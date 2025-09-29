package game;

import board.Board;
import entities.Player;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Display implements Runnable {
    private Board board;
    private BlockingQueue<UpdateEvent> eventQueue;
    private AtomicBoolean active;
    private Logger logger;

    public Display(Board board) {
        this.board = board;
        this.eventQueue = new LinkedBlockingQueue<>();
        this.active = new AtomicBoolean(false);
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void start() {
        active.set(true);
    }

    public void stop() {
        active.set(false);
    }

    @Override
    public void run() {
        log("Display started");

        // Mostrar tablero inicial
        printBoard();

        while (active.get()) {
            try {
                // Esperar evento con timeout
                UpdateEvent event = eventQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);

                if (event != null) {
                    processEvent(event);
                    printBoard();
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log("Display error: " + e.getMessage());
            }
        }

        log("Display stopped");
    }

    public void updateCell(int row, int col, Object occupant, Board.ItemType item, int quantity) {
        if (active.get()) {
            eventQueue.offer(new UpdateEvent(row, col, occupant, item, quantity));
        }
    }

    private void processEvent(UpdateEvent event) {
        // Solo para logging del evento
        String msg = "Cell (" + event.row + "," + event.col + ") updated";
        if (event.occupant != null) {
            msg += " - occupied by " + event.occupant;
        }
        if (event.item != null) {
            msg += " - item: " + event.item + "(" + event.quantity + ")";
        }
        log(msg);
    }

    private void printBoard() {
        System.out.println("\n========== BOARD ==========");
        System.out.println(board.printBoard());
        System.out.println("Lives: " + board.getLivesCount() +
                " | Coins: " + board.getCoinsCount() +
                " | Traps: " + board.getTrapsCount() +
                " | Coin cells: " + board.getCoinCells());
        System.out.println("===========================\n");
    }

    public void showGameStart() {
        System.out.println("=================================");
        System.out.println("       GAME STARTED!");
        System.out.println("=================================");
        printBoard();
    }

    public void showGameEnd() {
        System.out.println("=================================");
        System.out.println("       GAME ENDED!");
        System.out.println("=================================");
        printBoard();
    }

    public void showWinner(Player winner) {
        System.out.println("=================================");
        System.out.println("       WINNER!");
        System.out.println("  Player " + winner.getId() +
                " - Coins: " + winner.getCoins() +
                " - Lives: " + winner.getLives());
        System.out.println("=================================");
    }

    public void showResults(java.util.List<Player> players) {
        System.out.println("\n========== RESULTS ==========");
        for (Player p : players) {
            System.out.println("Player " + p.getId() +
                    " - Lives: " + p.getLives() +
                    " - Coins: " + p.getCoins() +
                    " - Status: " + (p.isAlive() ? "ALIVE" : "DEAD"));
        }
        System.out.println("=============================\n");
    }

    private void log(String msg) {
        if (logger != null) {
            logger.log("[DISPLAY] " + msg);
        }
    }

    // Clase para eventos de actualización
    private static class UpdateEvent {
        int row, col;
        Object occupant;
        Board.ItemType item;
        int quantity;

        UpdateEvent(int row, int col, Object occupant, Board.ItemType item, int quantity) {
            this.row = row;
            this.col = col;
            this.occupant = occupant;
            this.item = item;
            this.quantity = quantity;
        }
    }

    // Getters para debug
    public boolean isActive() { return active.get(); }
    public int getQueueSize() { return eventQueue.size(); }
}
