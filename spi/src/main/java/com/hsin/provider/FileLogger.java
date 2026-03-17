package com.hsin.provider;

import com.hsin.spi.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class FileLogger implements Logger {

    @Override
    public void log(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter("app.log", true))) {
            out.printf("[%s] %s%n", LocalDateTime.now(), message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
