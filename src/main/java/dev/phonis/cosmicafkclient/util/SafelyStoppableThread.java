package dev.phonis.cosmicafkclient.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class SafelyStoppableThread extends Thread {

    /*
     It is assumed that task is designed to deterministically stop when interrupted in an
     acceptable amount of time
     */
    public SafelyStoppableThread(Runnable task) {
        super(task);
    }

    // ASSUMES THAT THE THREAD HAS ALREADY BEEN INTERRUPTED
    public void waitForCompletion() throws InterruptedException {
        this.join();
    }

    public void safelyStop() throws InterruptedException {
        this.interrupt();
        this.waitForCompletion();
    }

    /*
    interrupt ALL threads before waiting for any of them, if the calling
    thread is interrupted while waiting for one of the SafelyStoppableThreads
    to complete, at least all the other threads were interrupted and can
    safely stop
     */
    public static void stopAll(SafelyStoppableThread... threads) throws InterruptedException {
        Arrays.stream(threads).sequential().filter(Objects::nonNull).forEach(Thread::interrupt);

        Iterator<SafelyStoppableThread> threadIterator = Arrays.stream(threads).sequential().filter(Objects::nonNull).iterator();

        while (threadIterator.hasNext()) {
            threadIterator.next().waitForCompletion();
        }
    }

}
