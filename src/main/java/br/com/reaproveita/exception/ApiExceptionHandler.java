package br.com.reaproveita.exception;

import br.com.reaproveita.dto.ApiError;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage() == null ? "invalido" : e.getDefaultMessage(), (a,b) -> a));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "Dados invalidos", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "JSON invalido ou valor de perfil desconhecido"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> known(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String description = ex.getReason() == null ? "Solicitacao rejeitada" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(ApiError.of(ex.getStatusCode().value(), description));
    }
}
