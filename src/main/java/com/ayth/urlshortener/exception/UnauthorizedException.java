package com.ayth.urlshortener.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String s) {
        super("Please login to continue");
    }
}
