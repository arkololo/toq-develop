package fr.toq.nutrition.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NutritionCalculator {
  public Totals calculate(List<Ingredient> ingredients, int servings) {
    BigDecimal kcal = BigDecimal.ZERO, protein = BigDecimal.ZERO, carbohydrate = BigDecimal.ZERO, fat = BigDecimal.ZERO;
    for (Ingredient item : ingredients) {
      BigDecimal factor = item.quantityG().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
      kcal = kcal.add(item.kcal100g().multiply(factor)); protein = protein.add(item.proteinG100g().multiply(factor)); carbohydrate = carbohydrate.add(item.carbohydrateG100g().multiply(factor)); fat = fat.add(item.fatG100g().multiply(factor));
    }
    return new Totals(round(kcal), round(protein), round(carbohydrate), round(fat), round(kcal.divide(BigDecimal.valueOf(servings), 6, RoundingMode.HALF_UP)), round(protein.divide(BigDecimal.valueOf(servings), 6, RoundingMode.HALF_UP)), round(carbohydrate.divide(BigDecimal.valueOf(servings), 6, RoundingMode.HALF_UP)), round(fat.divide(BigDecimal.valueOf(servings), 6, RoundingMode.HALF_UP)));
  }
  private BigDecimal round(BigDecimal value) { return value.setScale(1, RoundingMode.HALF_UP); }
  public record Ingredient(BigDecimal quantityG, BigDecimal kcal100g, BigDecimal proteinG100g, BigDecimal carbohydrateG100g, BigDecimal fatG100g) { }
  public record Totals(BigDecimal kcal, BigDecimal proteinG, BigDecimal carbohydrateG, BigDecimal fatG, BigDecimal kcalPerServing, BigDecimal proteinGPerServing, BigDecimal carbohydrateGPerServing, BigDecimal fatGPerServing) { }
}
