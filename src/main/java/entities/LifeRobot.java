package entities;

import board.Board;
import game.Logger;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class LifeRobot implements Runnable {
    private Board board;
    private Logger logger;
    private Random random;
    private AtomicBoolean gameActive;

    // Configuración
    private int maxLives = 5;  // X vidas máximo
    private int minSleep = 2000; // Xmin
    private int maxSleep = 4000; // Xmax

    public LifeRobot(Board board) {
        this.board = board;
        this.random = new Random();
        this.gameActive = new AtomicBoolean(false);
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setConfig(int maxLives, int minSleep, int maxSleep) {
        this.maxLives = maxLives;
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
        log("LifeRobot started");

        while (gameActive.get()) {
            try {
                if (board.getLivesCount() < maxLives) {
                    // Colocar vida
                    if (board.placeLife()) {
                        log("LifeRobot placed a life (total: " + board.getLivesCount() + ")");
                    } else {
                        log("LifeRobot couldn't place life - no free spots");
                    }
                } else {
                    // Esperar que tomen vidas
                    log("LifeRobot waiting - max lives reached (" + maxLives + ")");
                }

                sleep();

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log("LifeRobot error: " + e.getMessage());
            }
        }

        log("LifeRobot stopped");
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
    public int getMaxLives() { return maxLives; }
    public boolean isActive() { return gameActive.get(); }
}