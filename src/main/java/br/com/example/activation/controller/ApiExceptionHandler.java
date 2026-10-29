package br.com.example.activation.controller;

import br.com.example.activation.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        var p = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos não passaram na validação.", r);
        p.setProperty("errors", e.getBindingResult().getFieldErrors().stream().map(x -> Map.of("field", x.getField(), "message", Objects.toString(x.getDefaultMessage(), "valor inválido"))).toList());
        return ResponseEntity.badRequest().body(p);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest r) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "JSON inválido", "O corpo está ausente ou malformado.", r));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, "Acesso negado", "O escopo do token não permite esta operação.", r));
    }

    @ExceptionHandler(ActivationRequestNotFoundException.class)
    ResponseEntity<ProblemDetail> missing(HttpServletRequest r) {
        return ResponseEntity.status(404).body(problem(HttpStatus.NOT_FOUND, "Solicitação não encontrada", "Não existe solicitação com o identificador informado.", r));
    }

    @ExceptionHandler(DuplicateExternalIdException.class)
    ResponseEntity<ProblemDetail> duplicate(DuplicateExternalIdException e, HttpServletRequest r) {
        return ResponseEntity.status(409).body(problem(HttpStatus.CONFLICT, "Conflito de externalId", e.getMessage(), r));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest r) {
        return ResponseEntity.status(409).body(problem(HttpStatus.CONFLICT, "Conflito de dados", "A solicitação viola uma restrição de unicidade.", r));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(HttpServletRequest r) {
        return ResponseEntity.status(500).body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Não foi possível processar a solicitação.", r));
    }

    private ProblemDetail problem(HttpStatus s, String title, String detail, HttpServletRequest r) {
        var p = ProblemDetail.forStatusAndDetail(s, detail);
        p.setTitle(title);
        p.setType(URI.create("https://api.example.com/problems/" + s.value()));
        p.setInstance(URI.create(r.getRequestURI()));
        if (MDC.get("traceId") != null) p.setProperty("traceId", MDC.get("traceId"));
        return p;
    }
}
