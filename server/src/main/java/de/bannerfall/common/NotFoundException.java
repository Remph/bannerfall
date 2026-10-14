package de.bannerfall.common;

import org.springframework.http.HttpStatus;

/** Something doesn't exist or doesn't belong to the current player. → 404. */
public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code) {
        super(HttpStatus.NOT_FOUND, code, "Not found");
    }
}
