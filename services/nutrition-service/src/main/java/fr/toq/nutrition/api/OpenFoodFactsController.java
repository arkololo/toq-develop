package fr.toq.nutrition.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/nutrition/products")
public class OpenFoodFactsController {
  private final JdbcClient jdbc; private final ObjectMapper json = new ObjectMapper(); private final String baseUrl; private final String userAgent;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  public OpenFoodFactsController(JdbcClient jdbc, @Value("${toq.openfoodfacts.base-url}") String baseUrl, @Value("${toq.openfoodfacts.user-agent}") String userAgent) { this.jdbc = jdbc; this.baseUrl = baseUrl; this.userAgent = userAgent; }
  @GetMapping("/{barcode}") public Product product(@PathVariable String barcode) {
    if (!barcode.matches("[0-9]{8,14}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code-barres invalide.");
    Optional<Product> cached = jdbc.sql("SELECT barcode,name,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g,allergens,labels FROM food_cache WHERE barcode=:barcode").param("barcode", barcode).query((rs, row) -> new Product(rs.getString("barcode"),rs.getString("name"),rs.getBigDecimal("kcal_100g"),rs.getBigDecimal("protein_g_100g"),rs.getBigDecimal("carbohydrate_g_100g"),rs.getBigDecimal("fat_g_100g"),rs.getString("allergens"),rs.getString("labels"),true)).optional();
    if (cached.isPresent()) return cached.get();
    try { HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v3.6/product/" + barcode + ".json")).header("User-Agent", userAgent).timeout(Duration.ofSeconds(6)).GET().build(); var response = client.send(request, HttpResponse.BodyHandlers.ofString()); if (response.statusCode()==404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable."); if (response.statusCode()!=200) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenFoodFacts indisponible."); JsonNode root = json.readTree(response.body()); JsonNode product = root.path("product"); if (product.isMissingNode() || product.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable."); JsonNode nutrients = product.path("nutriments"); Product value = new Product(barcode, product.path("product_name").asText("Produit sans nom"), decimal(nutrients,"energy-kcal_100g"),decimal(nutrients,"proteins_100g"),decimal(nutrients,"carbohydrates_100g"),decimal(nutrients,"fat_100g"),product.path("allergens").asText(""),product.path("labels").asText(""),false); jdbc.sql("INSERT INTO food_cache(barcode,name,kcal_100g,protein_g_100g,carbohydrate_g_100g,fat_g_100g,allergens,labels,fetched_at) VALUES(:barcode,:name,:kcal,:protein,:carbs,:fat,:allergens,:labels,:now)").param("barcode",barcode).param("name",value.name()).param("kcal",value.kcal100g()).param("protein",value.proteinG100g()).param("carbs",value.carbohydrateG100g()).param("fat",value.fatG100g()).param("allergens",value.allergens()).param("labels",value.labels()).param("now",Timestamp.from(Instant.now())).update(); return value; } catch (ResponseStatusException exception) { throw exception; } catch (Exception exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenFoodFacts indisponible."); }
  }
  private java.math.BigDecimal decimal(JsonNode node, String name) { return node.path(name).isNumber() ? node.path(name).decimalValue() : null; }
  public record Product(String barcode,String name,java.math.BigDecimal kcal100g,java.math.BigDecimal proteinG100g,java.math.BigDecimal carbohydrateG100g,java.math.BigDecimal fatG100g,String allergens,String labels,boolean cached) { }
}
