package com.icthh.xm.ms.configuration.utils;

@FunctionalInterface
public interface ReturnableTask<R, E extends Exception> {
    R execute() throws E;
}