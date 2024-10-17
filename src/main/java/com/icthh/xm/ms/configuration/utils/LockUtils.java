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
    public static <R, E extends Exception> R runWithLock(Lock lock, long maxWaitTime, String operationName, ReturnableTask<R, E> task) {
        StopWatch stopWatch = StopWatch.createStarted();
        log.info("Try to lock " + operationName);
        if (lock.tryLock(maxWaitTime, TimeUnit.SECONDS)) {
            log.info(operationName + " locked");
            try {
                return task.execute();
            } finally {
                log.info("Try to unlock " + operationName);
                lock.unlock();
                log.info(operationName + " unlocked after {} ms", stopWatch.getTime());
            }
        } else {
            throw new IllegalMonitorStateException(operationName + " locked");
        }
    }

    public static <E extends Exception> void runWithLock(Lock lock, long maxWaitTime, String operationName, Task<E> task) {
        runWithLock(lock, maxWaitTime, operationName, () -> {
            task.execute();
            return null;
        });
    }

}
