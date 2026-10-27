package fr.toq.nutrition.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nutrition")
public class NutritionController {
  private final NutritionCalculator calculator;
  public NutritionController(NutritionCalculator calculator) { this.calculator = calculator; }
  @PostMapping("/calculate")
  public NutritionCalculator.Totals calculate(@Valid @RequestBody CalculationRequest request) {
    return calculator.calculate(request.ingredients().stream().map(item -> new NutritionCalculator.Ingredient(item.quantityG(), item.kcal100g(), item.proteinG100g(), item.carbohydrateG100g(), item.fatG100g())).toList(), request.servings());
  }
  public record CalculationRequest(@Positive @Max(100) int servings, @NotEmpty List<@Valid IngredientInput> ingredients) { }
  public record IngredientInput(@NotBlank String name, @NotNull @DecimalMin("0.001") BigDecimal quantityG, @NotNull @DecimalMin("0.0") BigDecimal kcal100g, @NotNull @DecimalMin("0.0") BigDecimal proteinG100g, @NotNull @DecimalMin("0.0") BigDecimal carbohydrateG100g, @NotNull @DecimalMin("0.0") BigDecimal fatG100g) { }
}
