package de.bannerfall.common;

import org.springframework.http.HttpStatus;

/** A request that breaks a game rule (not enough resources, username taken ...). → 409. */
public class GameRuleException extends ApiException {

    public GameRuleException(ErrorCode code, String detail) {
        super(HttpStatus.CONFLICT, code, detail);
    }
}
