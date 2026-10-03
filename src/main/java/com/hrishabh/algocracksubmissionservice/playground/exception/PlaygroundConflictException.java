package com.hrishabh.algocracksubmissionservice.playground.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PlaygroundConflictException extends RuntimeException {

    public PlaygroundConflictException(String message) {
        super(message);
    }
}
