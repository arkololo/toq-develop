package fr.toq.nutrition.api;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/nutrition/ciqual")
public class CiqualController {
  private final JdbcClient jdbc;
  private volatile List<CiqualFood> foods;
  public CiqualController(JdbcClient jdbc) { this.jdbc = jdbc; }

  @GetMapping
  public List<CiqualFood> search(@RequestParam String query) {
    String term = query.trim();
    if (term.length() < 2 || term.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saisissez 2 à 80 caractères.");
    String normalized = normalize(term);
    if (normalized.length() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saisissez au moins deux lettres ou chiffres.");
    List<String> words = List.of(normalized.split("\\s+"));
    return allFoods().stream().filter(food -> {
      String name = normalize(food.name());
      return words.stream().allMatch(name::contains);
    }).sorted(Comparator.comparingInt((CiqualFood food) -> {
      String name = normalize(food.name());
      return name.equals(normalized) ? 0 : name.startsWith(normalized) ? 1 : 2;
    }).thenComparing(CiqualFood::name)).limit(15).toList();
  }

  private List<CiqualFood> allFoods() {
    List<CiqualFood> cached = foods;
    if (cached != null) return cached;
    synchronized (this) {
      if (foods == null) foods = jdbc.sql("SELECT code,name,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g FROM ciqual_food")
          .query((rs, row) -> new CiqualFood(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("kcal_100g"),
              rs.getBigDecimal("protein_g_100g"), rs.getBigDecimal("carbohydrate_g_100g"), rs.getBigDecimal("fat_g_100g"))).list();
      return foods;
    }
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value.toLowerCase(java.util.Locale.FRENCH), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
  }

  @GetMapping("/{code}")
  public CiqualFood food(@PathVariable String code) {
    if (!code.matches("[0-9]{1,20}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code CIQUAL invalide.");
    return jdbc.sql("SELECT code,name,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g FROM ciqual_food WHERE code=:code")
        .param("code", code).query((rs, row) -> new CiqualFood(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("kcal_100g"),
            rs.getBigDecimal("protein_g_100g"), rs.getBigDecimal("carbohydrate_g_100g"), rs.getBigDecimal("fat_g_100g")))
        .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
  public record CiqualFood(String code, String name, BigDecimal kcal100g, BigDecimal proteinG100g,
                           BigDecimal carbohydrateG100g, BigDecimal fatG100g) {}
}
