package com.icthh.xm.ms.configuration.utils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.apache.commons.lang3.time.StopWatch;

@Slf4j
@UtilityClass
public class LockUtils {

    @SneakyThrows
    public static <R, E extends Exception> R runWithLock(Lock lock, long maxWaitTime, ReturnableTask<R, E> task) {
        StopWatch stopWatch = StopWatch.createStarted();
        log.info("Try to lock git repository");
        if (lock.tryLock(maxWaitTime, TimeUnit.SECONDS)) {
            log.info("Git repository locked");
            try {
                return task.execute();
            } finally {
                log.info("Try to unlock git repository");
                lock.unlock();
                log.info("Git repository unlocked after {} ms", stopWatch.getTime());
            }
        } else {
            throw new IllegalMonitorStateException("Git repository locked");
        }
    }

    public static <E extends Exception> void runWithLock(Lock lock, long maxWaitTime, Task<E> task) {
        runWithLock(lock, maxWaitTime, () -> {
            task.execute();
            return null;
        });
    }

}
