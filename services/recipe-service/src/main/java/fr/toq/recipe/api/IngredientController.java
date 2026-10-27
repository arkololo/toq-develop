package fr.toq.recipe.api;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, controlled ingredient catalogue used to help enter a recipe. */
@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientController {
  private final JdbcClient jdbc;

  public IngredientController(JdbcClient jdbc) { this.jdbc = jdbc; }

  @GetMapping
  public List<IngredientReference> search(@RequestParam(required = false) String query) {
    String suffix = query == null || query.isBlank() ? " ORDER BY name" : " WHERE LOWER(name) LIKE :query ORDER BY name";
    var statement = jdbc.sql("SELECT id, name FROM ingredient_reference" + suffix);
    if (query != null && !query.isBlank()) statement.param("query", "%" + query.trim().toLowerCase() + "%");
    return statement.query((rs, rowNum) -> new IngredientReference(rs.getObject("id", UUID.class), rs.getString("name"))).list();
  }

  public record IngredientReference(UUID id, String name) { }
}
