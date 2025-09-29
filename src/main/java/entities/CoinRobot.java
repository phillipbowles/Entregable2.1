package entities;

import board.Board;
import game.Logger;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoinRobot implements Runnable {
    private Board board;
    private Logger logger;
    private Random random;
    private AtomicBoolean gameActive;

    // Configuración
    private int minSleep = 1500; // Ymin
    private int maxSleep = 3500; // Ymax

    public CoinRobot(Board board) {
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
        log("CoinRobot started");

        while (gameActive.get()) {
            try {
                if (board.canPlaceCoins()) {
                    // Colocar monedas
                    if (board.placeCoins()) {
                        log("CoinRobot placed coins (coin cells: " + board.getCoinCells() + ")");
                    } else {
                        log("CoinRobot couldn't place coins - no free spots");
                    }
                } else {
                    // Esperar que tomen monedas
                    log("CoinRobot waiting - 10% of board has coins already");
                }

                sleep();

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log("CoinRobot error: " + e.getMessage());
            }
        }

        log("CoinRobot stopped");
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
}
