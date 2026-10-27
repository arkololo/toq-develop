package fr.toq.user.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail profileNotFound(ProfileNotFoundException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Profil introuvable");
        problem.setType(URI.create("https://toq.local/problems/profile-not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(InvalidGoalException.class)
    ProblemDetail invalidGoal(InvalidGoalException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("Objectif nutritionnel invalide");
        problem.setType(URI.create("https://toq.local/problems/invalid-nutrition-goal"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
