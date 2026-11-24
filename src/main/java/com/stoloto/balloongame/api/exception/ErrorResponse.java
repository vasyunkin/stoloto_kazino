package com.stoloto.balloongame.api.exception;

import org.springframework.http.HttpStatus;

/** RFC 9457 problem details returned by the REST API. */
public record ErrorResponse(String type, String title, int status, String detail) {

    public ErrorResponse(HttpStatus status, String detail) {
        this("about:blank", status.getReasonPhrase(), status.value(), detail);
    }

    public ErrorResponse(ErrorCode code, HttpStatus status, String detail) {
        this("https://balloon-game.local/problems/" + code.name().toLowerCase().replace('_', '-'),
                status.getReasonPhrase(), status.value(), detail);
    }
}
