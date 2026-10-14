package de.bannerfall.common;

import de.bannerfall.abuse.AbuseKind;
import de.bannerfall.abuse.AbuseService;
import de.bannerfall.account.AccountBannedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 ProblemDetail responses with an extra {@code code} property. The
 * base class already covers Spring MVC's own exceptions (405, 415, ...). Stack traces never leave
 * the server.
 *
 * <p>Answers the web client never causes in normal play go to the suspicion log as well (M14e):
 * every 400, and a 404 for an id that exists but is someone else's ({@link AbuseService}).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Request attribute: why the body couldn't be read (a 400 that isn't a field rule). */
    private static final String MALFORMED = ApiExceptionHandler.class.getName() + ".malformed";

    private final AbuseService abuse;

    ApiExceptionHandler(AbuseService abuse) {
        this.abuse = abuse;
    }

    /** One invalid field of a request body, or one invalid query parameter. */
    record FieldErrorDto(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException e, HttpServletRequest request) {
        if (e instanceof NotFoundException) {
            abuse.notFound(request, e.code());
        } else if (e.status() == HttpStatus.BAD_REQUEST) {
            abuse.suspect(request, AbuseKind.INVALID_INPUT, e.getMessage());
        }
        ProblemDetail problem = problem(e.status(), e.code(), e.getMessage());
        e.properties().forEach(problem::setProperty);
        if (e instanceof AccountBannedException banned) {
            problem.setProperty("bannedUntil", banned.bannedUntil().toString());
            problem.setProperty("reason", banned.reason() == null ? "" : banned.reason());
        }
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldErrorDto> errors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> new FieldErrorDto(f.getField(), f.getDefaultMessage()))
                        .toList();
        ProblemDetail body =
                problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "Invalid request");
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body =
                problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "Malformed request body");
        body.setProperty("errors", List.of());
        // what was wrong (e.g. an unknown enum value), for the suspicion log only
        request.setAttribute(
                MALFORMED,
                String.valueOf(e.getMostSpecificCause().getMessage()),
                WebRequest.SCOPE_REQUEST);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    /**
     * Constraint annotations on controller parameters ({@code @RequestParam @Max(60) int w}):
     * Spring checks them by itself. The parameter name goes into {@code field}, like a body field.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (e.isForReturnValue()) {
            // an invalid answer is our bug, not the caller's
            return super.handleHandlerMethodValidationException(e, headers, status, request);
        }
        List<FieldErrorDto> errors = new ArrayList<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            String parameter = parameterName(result.getMethodParameter());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldErrorDto(parameter, error.getDefaultMessage()));
            }
        }
        ProblemDetail body =
                problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "Invalid request");
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    /**
     * Every answer of the base class (and of the handlers above) ends here. Spring's own errors
     * (400 for {@code /api/villages/abc}, 404 for an unknown path, 405, 415 ...) come without our
     * {@code code}; add one so the web app can show a translated message.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem && !hasCode(problem)) {
            problem.setProperty("code", codeFor(status).name());
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()
                && request instanceof NativeWebRequest web
                && web.getNativeRequest(HttpServletRequest.class) instanceof HttpServletRequest r) {
            Object malformed = request.getAttribute(MALFORMED, WebRequest.SCOPE_REQUEST);
            if (malformed != null) {
                abuse.suspect(r, AbuseKind.MALFORMED_BODY, malformed.toString());
            } else {
                abuse.suspect(r, AbuseKind.INVALID_INPUT, describe(body));
            }
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Unexpected server error");
    }

    /** The name the caller used: {@code @RequestParam("name")} if given, else the Java name. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name =
                    requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
            if (!name.isEmpty()) {
                return name;
            }
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    /** The detail and the field errors of a 400, for the suspicion log. */
    private static String describe(Object body) {
        if (!(body instanceof ProblemDetail problem)) {
            return "";
        }
        Object errors =
                problem.getProperties() == null ? null : problem.getProperties().get("errors");
        return errors instanceof List<?> list && !list.isEmpty()
                ? problem.getDetail() + " " + list
                : String.valueOf(problem.getDetail());
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.VALIDATION;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        return status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
