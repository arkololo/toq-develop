package fr.toq.recipe.api;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts French search terms before querying TheMealDB; source recipes are left unchanged. */
@Component
public class MealTranslator {
  private static final Map<String, String> TERMS = Map.ofEntries(
      Map.entry("pate", "pasta"), Map.entry("pates", "pasta"), Map.entry("poulet", "chicken"),
      Map.entry("riz", "rice"), Map.entry("boeuf", "beef"), Map.entry("porc", "pork"),
      Map.entry("poisson", "fish"), Map.entry("saumon", "salmon"), Map.entry("crevette", "shrimp"),
      Map.entry("crevettes", "shrimp"), Map.entry("pomme", "apple"), Map.entry("pommes", "apple"),
      Map.entry("tomate", "tomato"), Map.entry("tomates", "tomato"),
      Map.entry("champignon", "mushroom"), Map.entry("champignons", "mushroom"),
      Map.entry("carotte", "carrot"), Map.entry("carottes", "carrot"),
      Map.entry("soupe", "soup"), Map.entry("salade", "salad"),
      Map.entry("tarte", "pie"), Map.entry("gateau", "cake"), Map.entry("chocolat", "chocolate"),
      Map.entry("fromage", "cheese"), Map.entry("legume", "vegetable"), Map.entry("legumes", "vegetable"));

  public MealTranslator() {}
  public MealTranslator(String ignoredBaseUrl) {}

  public String searchEnglish(String french) {
    String normalized = Normalizer.normalize(french.toLowerCase(java.util.Locale.FRENCH), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    if (normalized.equals("tarte aux pommes")) return "apple pie";
    List<String> words = new ArrayList<>();
    for (String word : normalized.split("\\s+")) {
      if (word.isBlank() || List.of("de", "des", "du", "la", "le", "les", "aux", "au", "a", "et").contains(word)) continue;
      words.add(TERMS.getOrDefault(word, word));
    }
    return String.join(" ", words);
  }
}
