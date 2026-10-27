package fr.toq.recipe.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RecipeExceptionHandler {
  @ExceptionHandler(RecipeNotFoundException.class)
  ProblemDetail missingRecipe() {
    ProblemDetail problem = ProblemDetail.forStatus(404);
    problem.setDetail("Recette introuvable.");
    return problem;
  }
}
