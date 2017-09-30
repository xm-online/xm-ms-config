package com.icthh.xm.ms.configuration.utils;

@FunctionalInterface
public interface Task<E extends Exception> {
    void execute() throws E;
}
