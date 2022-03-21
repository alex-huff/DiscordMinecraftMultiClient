package dev.phonis.discordminecraftmulticlient.util;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class LockUtils
{

    @FunctionalInterface
    public interface IO
    {

        void run() throws IOException;

    }

    @FunctionalInterface
    public interface InterruptableProducer<T>
    {

        T get() throws InterruptedException;

    }

    @FunctionalInterface
    public interface Interruptable
    {

        void run() throws InterruptedException;

    }

    @FunctionalInterface
    public interface InterruptableIO
    {

        void run() throws InterruptedException, IOException;

    }

    public static void withLockIO(ReentrantLock lock, IO io) throws IOException
    {
        lock.lock();

        try
        {
            io.run();
        }
        finally
        {
            lock.unlock();
        }
    }

    public static void withLockInterruptable(ReentrantLock lock, Interruptable interruptable)
        throws InterruptedException
    {
        lock.lock();

        try
        {
            interruptable.run();
        }
        finally
        {
            lock.unlock();
        }
    }

    public static void withLockInterruptableIO(ReentrantLock lock, InterruptableIO interruptable)
        throws InterruptedException, IOException
    {
        lock.lock();

        try
        {
            interruptable.run();
        }
        finally
        {
            lock.unlock();
        }
    }

    public static <T> T getWithLockInterruptable(ReentrantLock lock, InterruptableProducer<T> producer)
        throws InterruptedException
    {
        lock.lock();

        try
        {
            return producer.get();
        }
        finally
        {
            lock.unlock();
        }
    }

    public static void withLock(ReentrantLock lock, Runnable runnable)
    {
        lock.lock();

        try
        {
            runnable.run();
        }
        finally
        {
            lock.unlock();
        }
    }

}
