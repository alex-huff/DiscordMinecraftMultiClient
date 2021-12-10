package dev.phonis.cosmicafkclient.util;

public interface ExponentialBackoff {

    default void backoff() throws InterruptedException {
        this.waitUntilReady();
        this.onFailure();
    }

    void onSuccess();

    void onFailure();

    long getWaitTime();

    default void waitUntilReady() throws InterruptedException {
        Thread.sleep(this.getWaitTime());
    }

}
