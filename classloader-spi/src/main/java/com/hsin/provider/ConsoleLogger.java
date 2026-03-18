package com.hsin.provider;

import com.hsin.spi.Logger;

public class ConsoleLogger implements Logger {

    @Override
    public void log(String message) {
        System.out.println("[Console] " + message);
    }
}
