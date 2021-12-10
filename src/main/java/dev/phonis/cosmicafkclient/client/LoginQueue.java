package dev.phonis.cosmicafkclient.client;

import dev.phonis.cosmicafkclient.CosmicAFKClient;
import dev.phonis.cosmicafkclient.util.Waiter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LoginQueue {

    private static final BlockingQueue<Waiter> loginQueue = new LinkedBlockingQueue<>();
    public static final Thread restarterThread = new Thread(
        () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    LoginQueue.loginQueue.take().wake();
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            }

            CosmicAFKClient.log("Closing restarter thread");
        }
    );

    static {
        LoginQueue.restarterThread.start();
    }

    public static void waitForTurn() throws InterruptedException {
        Waiter waiter = new Waiter();

        LoginQueue.loginQueue.put(waiter);

        waiter.rest();
    }

}