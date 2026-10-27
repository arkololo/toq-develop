package fr.toq.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import fr.toq.nutrition.api.NutritionCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NutritionCalculatorTest {
  private final NutritionCalculator calculator = new NutritionCalculator();
  @Test void calculates_recipe_and_portion_totals() {
    var totals = calculator.calculate(List.of(new NutritionCalculator.Ingredient(new BigDecimal("150"), new BigDecimal("130"), new BigDecimal("2.7"), new BigDecimal("28"), new BigDecimal("0.3"))), 2);
    assertThat(totals.kcal()).isEqualByComparingTo("195.0");
    assertThat(totals.carbohydrateG()).isEqualByComparingTo("42.0");
    assertThat(totals.kcalPerServing()).isEqualByComparingTo("97.5");
  }
}
