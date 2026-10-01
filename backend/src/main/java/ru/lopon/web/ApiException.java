package ru.lopon.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ApiException extends ErrorResponseException {

    public ApiException(HttpStatus status, String detail) {
        this(status, detail, Map.of());
    }

    public ApiException(HttpStatus status, String detail, Map<String, Object> properties) {
        super(status, problem(status, detail, properties), null);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " не найден");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, Map<String, Object> properties) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        properties.forEach(problem::setProperty);
        return problem;
    }
}
