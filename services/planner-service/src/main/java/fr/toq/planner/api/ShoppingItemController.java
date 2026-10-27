package fr.toq.planner.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/shopping-lists")
public class ShoppingItemController {
  private final JdbcClient jdbc;
  public ShoppingItemController(JdbcClient jdbc) { this.jdbc = jdbc; }

  @PatchMapping("/{listId}/items/{itemId}")
  public void check(@PathVariable UUID listId, @PathVariable UUID itemId, @AuthenticationPrincipal Jwt jwt, @RequestBody CheckRequest request) {
    int changed = jdbc.sql("UPDATE shopping_item SET checked=:checked WHERE id=:item AND list_id=:list AND EXISTS (SELECT 1 FROM shopping_list s JOIN week_plan p ON p.id=s.plan_id WHERE s.id=:list AND p.owner_sub=:owner)")
        .param("checked", request.checked()).param("item", itemId).param("list", listId).param("owner", jwt.getSubject()).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article introuvable.");
  }
  public record CheckRequest(boolean checked) { }
}
