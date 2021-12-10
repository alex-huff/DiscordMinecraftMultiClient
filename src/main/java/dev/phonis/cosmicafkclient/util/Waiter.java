package dev.phonis.cosmicafkclient.util;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Waiter {

    private final ReentrantLock waitLock = new ReentrantLock();
    private final Condition completeSignal = waitLock.newCondition();
    private boolean complete = false;

    public void rest() throws InterruptedException {
        LockUtils.withLockInterruptable(this.waitLock,
            () -> {
                while (!this.complete) {
                    this.completeSignal.await();
                }
            }
        );
    }

    public void wake() {
        LockUtils.withLock(this.waitLock,
            () -> {
                this.complete = true;

                this.completeSignal.signalAll();
            }
        );
    }

}
