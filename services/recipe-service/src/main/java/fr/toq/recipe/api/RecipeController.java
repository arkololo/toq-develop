package fr.toq.recipe.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {
  private final JdbcClient jdbc;
  public RecipeController(JdbcClient jdbc) { this.jdbc = jdbc; }

  @GetMapping public List<RecipeResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) String query, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
    if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("Pagination invalide.");
    String sql = "SELECT id,title,description,base_servings,version,kcal_per_serving,protein_g_per_serving,carbohydrate_g_per_serving,fat_g_per_serving,source_template_id FROM recipe WHERE owner_sub=:owner AND status='ACTIVE'" + (query == null || query.isBlank() ? " ORDER BY updated_at DESC" : " AND LOWER(title) LIKE :query ORDER BY updated_at DESC") + " LIMIT :limit OFFSET :offset";
    var statement = jdbc.sql(sql).param("owner", subject(jwt));
    if (query != null && !query.isBlank()) statement.param("query", "%" + query.trim().toLowerCase() + "%");
    statement.param("limit", size).param("offset", page * size);
    return statement.query((rs, row) -> response(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"), rs.getInt("base_servings"), rs.getLong("version"), rs.getBigDecimal("kcal_per_serving"), rs.getBigDecimal("protein_g_per_serving"), rs.getBigDecimal("carbohydrate_g_per_serving"), rs.getBigDecimal("fat_g_per_serving"), rs.getString("source_template_id"))).list();
  }

  @PostMapping @Transactional public ResponseEntity<RecipeResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecipeRequest request) {
    UUID id = UUID.randomUUID(); Timestamp now = Timestamp.from(Instant.now());
    jdbc.sql("INSERT INTO recipe(id,owner_sub,title,description,base_servings,kcal_per_serving,protein_g_per_serving,carbohydrate_g_per_serving,fat_g_per_serving,source_template_id,status,version,created_at,updated_at) VALUES (:id,:owner,:title,:description,:servings,:kcal,:protein,:carbs,:fat,:source,'ACTIVE',0,:now,:now)")
        .param("id",id).param("owner",subject(jwt)).param("title",request.title().trim()).param("description",request.description()).param("servings",request.baseServings())
        .param("kcal",request.kcalPerServing()).param("protein",request.proteinGPerServing()).param("carbs",request.carbohydrateGPerServing()).param("fat",request.fatGPerServing())
        .param("source",request.sourceTemplateId()).param("now",now).update();
    replaceLines(id, request); return ResponseEntity.status(HttpStatus.CREATED).body(get(id, jwt));
  }

  @PostMapping("/batch") @Transactional public ResponseEntity<List<RecipeResponse>> createBatch(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody List<@Valid RecipeRequest> requests) {
    if (requests == null || requests.isEmpty() || requests.size() > 10)
      throw new IllegalArgumentException("Un lot contient de 1 à 10 recettes.");
    return ResponseEntity.status(HttpStatus.CREATED).body(requests.stream().map(request -> create(jwt, request).getBody()).toList());
  }

  @GetMapping("/{id}") public RecipeResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return owned(id, subject(jwt)); }
  @PutMapping("/{id}") @Transactional public RecipeResponse update(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecipeRequest request) {
    owned(id, subject(jwt)); Timestamp now = Timestamp.from(Instant.now());
    jdbc.sql("UPDATE recipe SET title=:title,description=:description,base_servings=:servings,kcal_per_serving=:kcal,protein_g_per_serving=:protein,carbohydrate_g_per_serving=:carbs,fat_g_per_serving=:fat,version=version+1,updated_at=:now WHERE id=:id")
        .param("id",id).param("title",request.title().trim()).param("description",request.description()).param("servings",request.baseServings())
        .param("kcal",request.kcalPerServing()).param("protein",request.proteinGPerServing()).param("carbs",request.carbohydrateGPerServing()).param("fat",request.fatGPerServing()).param("now",now).update();
    replaceLines(id, request); return get(id, jwt);
  }
  @DeleteMapping("/{id}") @Transactional public ResponseEntity<Void> archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { owned(id,subject(jwt)); jdbc.sql("UPDATE recipe SET status='ARCHIVED',version=version+1 WHERE id=:id").param("id",id).update(); return ResponseEntity.noContent().build(); }

  private void replaceLines(UUID recipeId, RecipeRequest request) {
    jdbc.sql("DELETE FROM recipe_ingredient WHERE recipe_id=:id").param("id",recipeId).update(); jdbc.sql("DELETE FROM recipe_step WHERE recipe_id=:id").param("id",recipeId).update();
    for (int i=0;i<request.ingredients().size();i++) { IngredientRequest item=request.ingredients().get(i); jdbc.sql("INSERT INTO recipe_ingredient(id,recipe_id,name,quantity,unit,position,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g) VALUES (:id,:recipe,:name,:quantity,:unit,:position,:kcal,:protein,:carbs,:fat)").param("id",UUID.randomUUID()).param("recipe",recipeId).param("name",item.name().trim()).param("quantity",item.quantity()).param("unit",item.unit().trim()).param("position",i+1).param("kcal",item.kcal100g()).param("protein",item.proteinG100g()).param("carbs",item.carbohydrateG100g()).param("fat",item.fatG100g()).update(); }
    for (int i=0;i<request.steps().size();i++) { String step=request.steps().get(i).trim(); jdbc.sql("INSERT INTO recipe_step(id,recipe_id,instruction,position) VALUES (:id,:recipe,:instruction,:position)").param("id",UUID.randomUUID()).param("recipe",recipeId).param("instruction",step).param("position",i+1).update(); }
  }
  private RecipeResponse owned(UUID id,String owner) { return jdbc.sql("SELECT id,title,description,base_servings,version,kcal_per_serving,protein_g_per_serving,carbohydrate_g_per_serving,fat_g_per_serving,source_template_id FROM recipe WHERE id=:id AND owner_sub=:owner AND status='ACTIVE'").param("id",id).param("owner",owner).query((rs,row)->response(id,rs.getString("title"),rs.getString("description"),rs.getInt("base_servings"),rs.getLong("version"),rs.getBigDecimal("kcal_per_serving"),rs.getBigDecimal("protein_g_per_serving"),rs.getBigDecimal("carbohydrate_g_per_serving"),rs.getBigDecimal("fat_g_per_serving"),rs.getString("source_template_id"))).optional().orElseThrow(() -> new RecipeNotFoundException()); }
  private RecipeResponse response(UUID id,String title,String description,int servings,long version,BigDecimal kcal,BigDecimal protein,BigDecimal carbs,BigDecimal fat,String source) { List<IngredientResponse> ingredients=jdbc.sql("SELECT name,quantity,unit,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g FROM recipe_ingredient WHERE recipe_id=:id ORDER BY position").param("id",id).query((rs,row)->new IngredientResponse(rs.getString("name"),rs.getBigDecimal("quantity"),rs.getString("unit"),rs.getBigDecimal("kcal_100g"),rs.getBigDecimal("protein_g_100g"),rs.getBigDecimal("carbohydrate_g_100g"),rs.getBigDecimal("fat_g_100g"))).list(); List<String> steps=jdbc.sql("SELECT instruction FROM recipe_step WHERE recipe_id=:id ORDER BY position").param("id",id).query(String.class).list(); boolean hasImage=jdbc.sql("SELECT COUNT(*) FROM recipe_image WHERE recipe_id=:id").param("id",id).query(Long.class).single()>0; return new RecipeResponse(id,title,description,servings,ingredients,steps,version,hasImage,kcal,protein,carbs,fat,source); }
  private String subject(Jwt jwt) { if (jwt.getSubject()==null) throw new IllegalStateException("Jeton sans sujet"); return jwt.getSubject(); }
  public record RecipeRequest(@NotBlank @Size(max=120) String title, @Size(max=2000) String description, @Positive int baseServings, @NotEmpty List<@Valid IngredientRequest> ingredients, @NotEmpty List<@NotBlank @Size(max=2000) String> steps,
                              @DecimalMin("0.0") BigDecimal kcalPerServing, @DecimalMin("0.0") BigDecimal proteinGPerServing,
                              @DecimalMin("0.0") BigDecimal carbohydrateGPerServing, @DecimalMin("0.0") BigDecimal fatGPerServing,
                              @Pattern(regexp="[0-9]{1,12}") String sourceTemplateId) {
    public RecipeRequest(String title, String description, int baseServings, List<IngredientRequest> ingredients, List<String> steps) {
      this(title, description, baseServings, ingredients, steps, null, null, null, null, null);
    }
    @AssertTrue(message="Renseignez les quatre valeurs nutritionnelles par portion ou laissez-les toutes vides.")
    public boolean isNutritionCompleteOrEmpty() {
      int filled = (kcalPerServing != null ? 1 : 0) + (proteinGPerServing != null ? 1 : 0)
          + (carbohydrateGPerServing != null ? 1 : 0) + (fatGPerServing != null ? 1 : 0);
      return filled == 0 || filled == 4;
    }
  }
  public record IngredientRequest(@NotBlank @Size(max=120) String name, @NotNull @DecimalMin("0.001") BigDecimal quantity, @NotBlank @Size(max=20) String unit, @DecimalMin("0.0") BigDecimal kcal100g, @DecimalMin("0.0") BigDecimal proteinG100g, @DecimalMin("0.0") BigDecimal carbohydrateG100g, @DecimalMin("0.0") BigDecimal fatG100g) { }
  public record RecipeResponse(UUID id,String title,String description,int baseServings,List<IngredientResponse> ingredients,List<String> steps,long version,boolean hasImage,
                               BigDecimal kcalPerServing,BigDecimal proteinGPerServing,BigDecimal carbohydrateGPerServing,BigDecimal fatGPerServing,String sourceTemplateId) { }
  public record IngredientResponse(String name,BigDecimal quantity,String unit,BigDecimal kcal100g,BigDecimal proteinG100g,BigDecimal carbohydrateG100g,BigDecimal fatG100g) { }
}
