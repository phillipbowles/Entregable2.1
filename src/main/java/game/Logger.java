package game;

import entities.Player;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Logger implements Runnable {
    private BlockingQueue<String> messageQueue;
    private AtomicBoolean active;
    private PrintWriter fileWriter;
    private String filename;
    private SimpleDateFormat timeFormat;

    public Logger(String filename) {
        this.filename = filename;
        this.messageQueue = new LinkedBlockingQueue<>();
        this.active = new AtomicBoolean(false);
        this.timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

        try {
            this.fileWriter = new PrintWriter(new FileWriter(filename, true));
        } catch (IOException e) {
            System.err.println("Error creating log file: " + e.getMessage());
        }
    }

    public void start() {
        active.set(true);
        log("=== GAME LOG STARTED ===");
    }

    public void stop() {
        log("=== GAME LOG ENDED ===");
        active.set(false);
    }

    @Override
    public void run() {
        while (active.get() || !messageQueue.isEmpty()) {
            try {
                // Esperar mensaje con timeout
                String message = messageQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);

                if (message != null) {
                    writeToFile(message);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Logger error: " + e.getMessage());
            }
        }

        // Cerrar archivo
        if (fileWriter != null) {
            fileWriter.close();
        }
    }

    public void log(String message) {
        if (active.get()) {
            messageQueue.offer(message);
        }
    }

    private void writeToFile(String message) {
        if (fileWriter != null) {
            String timestamp = timeFormat.format(new Date());
            String logEntry = "[" + timestamp + "] " + message;

            // Escribir a archivo
            fileWriter.println(logEntry);
            fileWriter.flush();

            // También mostrar en consola para debug
            System.out.println(logEntry);
        }
    }

    // Métodos especiales para eventos importantes
    public void logGameStart(int boardSize, int numPlayers) {
        log("GAME START - Board: " + boardSize + "x" + boardSize + " - Players: " + numPlayers);
    }

    public void logGameEnd(String reason) {
        log("GAME END - Reason: " + reason);
    }

    public void logPlayerDeath(int playerId) {
        log("PLAYER DEATH - Player " + playerId + " has died");
    }

    public void logWinner(Player winner) {
        log("WINNER - Player " + winner.getId() +
                " with " + winner.getCoins() + " coins and " + winner.getLives() + " lives");
    }

    public void logResults(java.util.List<Player> players) {
        log("FINAL RESULTS:");
        for (Player p : players) {
            log("  Player " + p.getId() + " - Lives: " + p.getLives() +
                    " - Coins: " + p.getCoins() + " - Status: " + (p.isAlive() ? "ALIVE" : "DEAD"));
        }
    }

    // Getters para debug
    public boolean isActive() { return active.get(); }
    public int getQueueSize() { return messageQueue.size(); }
    public String getFilename() { return filename; }
}