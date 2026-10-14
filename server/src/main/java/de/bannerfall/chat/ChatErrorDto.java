package de.bannerfall.chat;

import de.bannerfall.common.ErrorCode;

/**
 * A refused chat message, sent to its author only on {@code /user/queue/errors} (STOMP has no
 * response to a SEND). {@code code} is the same as in a REST ProblemDetail: {@code VALIDATION} or
 * {@code CHAT_RATE_LIMIT}.
 */
public record ChatErrorDto(ErrorCode code, String detail) {}
