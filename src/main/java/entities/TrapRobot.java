package entities;

import board.Board;
import game.Logger;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrapRobot implements Runnable {
    private Board board;
    private Logger logger;
    private Random random;
    private AtomicBoolean gameActive;

    // Configuración
    private int minSleep = 2500; // Wmin
    private int maxSleep = 5000; // Wmax

    public TrapRobot(Board board) {
        this.board = board;
        this.random = new Random();
        this.gameActive = new AtomicBoolean(false);
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setConfig(int minSleep, int maxSleep) {
        this.minSleep = minSleep;
        this.maxSleep = maxSleep;
    }

    public void startGame() {
        gameActive.set(true);
    }

    public void stopGame() {
        gameActive.set(false);
    }

    @Override
    public void run() {
        log("TrapRobot started");

        while (gameActive.get() && board.canPlaceTraps()) {
            try {
                // Intentar colocar trampa
                if (board.placeTrap()) {
                    log("TrapRobot placed trap (total traps: " + board.getTrapsCount() + ")");
                } else {
                    log("TrapRobot couldn't place trap - no free spots");
                }

                // Verificar si alcanzó el máximo
                if (!board.canPlaceTraps()) {
                    log("TrapRobot reached maximum traps (10% of board). Stopping work.");
                    break;
                }

                sleep();

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log("TrapRobot error: " + e.getMessage());
            }
        }

        log("TrapRobot finished its work");
    }

    private void sleep() throws InterruptedException {
        int sleepTime = random.nextInt(maxSleep - minSleep + 1) + minSleep;
        Thread.sleep(sleepTime);
    }

    private void log(String msg) {
        if (logger != null) {
            logger.log(msg);
        }
    }

    // Getters para debug
    public boolean isActive() { return gameActive.get(); }
    public boolean hasFinishedWork() { return !board.canPlaceTraps(); }
}