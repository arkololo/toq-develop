package fr.toq.user.application;

import fr.toq.user.api.InvalidGoalException;
import fr.toq.user.api.ProfileNotFoundException;
import fr.toq.user.api.UserProfileController.NutritionGoalRequest;
import fr.toq.user.api.UserProfileController.NutritionGoalResponse;
import fr.toq.user.api.UserProfileController.PreferencesRequest;
import fr.toq.user.api.UserProfileController.PreferencesResponse;
import fr.toq.user.api.UserProfileController.ProfileRequest;
import fr.toq.user.api.UserProfileController.UserProfileResponse;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private static final String DEFAULT_TIMEZONE = "Europe/Paris";
    private static final String DEFAULT_LOCALE = "fr-FR";
    private final JdbcClient jdbc;
    private final Clock clock;

    public UserProfileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String subject) {
        return profile(subject).orElseThrow(ProfileNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Map<String, String> displayNames(List<String> subjects) {
        if (subjects.isEmpty()) return Map.of();
        return jdbc.sql("SELECT keycloak_sub,display_name FROM user_profile WHERE status='ACTIVE' AND keycloak_sub IN (:subjects)")
                .param("subjects", subjects).query((rs, row) -> Map.entry(rs.getString("keycloak_sub"), rs.getString("display_name")))
                .list().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional
    public UserProfileResponse saveProfile(Jwt jwt, ProfileRequest request) {
        Timestamp now = Timestamp.from(clock.instant());
        var existing = profile(jwt.getSubject());
        if (existing.isPresent()) {
            jdbc.sql("UPDATE user_profile SET display_name=:name, version=version+1, updated_at=:now WHERE keycloak_sub=:subject")
                    .param("name", request.displayName().trim()).param("now", now).param("subject", jwt.getSubject()).update();
        } else {
            jdbc.sql("INSERT INTO user_profile(id,keycloak_sub,display_name,timezone,locale,status,version,created_at,updated_at) VALUES (:id,:subject,:name,:timezone,:locale,'ACTIVE',0,:now,:now)")
                    .param("id", UUID.randomUUID()).param("subject", jwt.getSubject())
                    .param("name", request.displayName().trim()).param("timezone", DEFAULT_TIMEZONE)
                    .param("locale", DEFAULT_LOCALE).param("now", now).update();
        }
        return getProfile(jwt.getSubject());
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences(String subject) {
        UUID id = profileId(subject);
        return new PreferencesResponse(stringList("SELECT code FROM dietary_preference WHERE profile_id=:id ORDER BY code", id),
                stringList("SELECT allergen_code FROM allergy WHERE profile_id=:id ORDER BY allergen_code", id),
                uuidList("SELECT ingredient_reference_id FROM excluded_ingredient WHERE profile_id=:id ORDER BY ingredient_reference_id", id));
    }

    @Transactional
    public PreferencesResponse savePreferences(String subject, PreferencesRequest request) {
        UUID id = profileId(subject);
        List<String> preferences = distinctUpper(request.dietaryPreferenceCodes());
        List<String> allergies = distinctUpper(request.allergyCodes());
        List<UUID> excluded = request.excludedIngredientIds().stream().distinct().sorted(Comparator.naturalOrder()).toList();
        jdbc.sql("DELETE FROM dietary_preference WHERE profile_id=:id").param("id", id).update();
        jdbc.sql("DELETE FROM allergy WHERE profile_id=:id").param("id", id).update();
        jdbc.sql("DELETE FROM excluded_ingredient WHERE profile_id=:id").param("id", id).update();
        preferences.forEach(code -> jdbc.sql("INSERT INTO dietary_preference(profile_id,code) VALUES (:id,:code)").param("id", id).param("code", code).update());
        allergies.forEach(code -> jdbc.sql("INSERT INTO allergy(profile_id,allergen_code) VALUES (:id,:code)").param("id", id).param("code", code).update());
        excluded.forEach(ingredientId -> jdbc.sql("INSERT INTO excluded_ingredient(profile_id,ingredient_reference_id) VALUES (:id,:ingredient)").param("id", id).param("ingredient", ingredientId).update());
        return new PreferencesResponse(preferences, allergies, excluded);
    }

    @Transactional(readOnly = true)
    public List<NutritionGoalResponse> getGoals(String subject) {
        UUID id = profileId(subject);
        return jdbc.sql("SELECT id,valid_from,valid_to,kcal,protein_g,carbohydrate_g,fat_g,version FROM nutrition_goal WHERE profile_id=:id ORDER BY valid_from DESC")
                .param("id", id).query((rs, row) -> new NutritionGoalResponse(
                        rs.getObject("id", UUID.class), rs.getDate("valid_from").toLocalDate(),
                        rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toLocalDate(),
                        rs.getBigDecimal("kcal"), rs.getBigDecimal("protein_g"), rs.getBigDecimal("carbohydrate_g"),
                        rs.getBigDecimal("fat_g"), rs.getLong("version"))).list();
    }

    @Transactional
    public NutritionGoalResponse saveGoal(String subject, NutritionGoalRequest request) {
        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new InvalidGoalException("La date de fin doit être postérieure ou égale à la date de début.");
        }
        if (request.kcal() == null && request.proteinG() == null && request.carbohydrateG() == null && request.fatG() == null) {
            throw new InvalidGoalException("Définissez au moins une cible nutritionnelle.");
        }
        UUID profileId = profileId(subject);
        Timestamp now = Timestamp.from(clock.instant());
        UUID goalId = jdbc.sql("SELECT id FROM nutrition_goal WHERE profile_id=:profile AND valid_from=:validFrom")
                .param("profile", profileId).param("validFrom", request.validFrom()).query(UUID.class).optional().orElse(null);
        if (goalId == null) {
            goalId = UUID.randomUUID();
            jdbc.sql("INSERT INTO nutrition_goal(id,profile_id,valid_from,valid_to,kcal,protein_g,carbohydrate_g,fat_g,version,created_at,updated_at) VALUES (:id,:profile,:validFrom,:validTo,:kcal,:protein,:carbohydrate,:fat,0,:now,:now)")
                    .param("id", goalId).param("profile", profileId).param("validFrom", request.validFrom()).param("validTo", request.validTo())
                    .param("kcal", request.kcal()).param("protein", request.proteinG()).param("carbohydrate", request.carbohydrateG()).param("fat", request.fatG()).param("now", now).update();
        } else {
            jdbc.sql("UPDATE nutrition_goal SET valid_to=:validTo,kcal=:kcal,protein_g=:protein,carbohydrate_g=:carbohydrate,fat_g=:fat,version=version+1,updated_at=:now WHERE id=:id")
                    .param("id", goalId).param("validTo", request.validTo()).param("kcal", request.kcal()).param("protein", request.proteinG()).param("carbohydrate", request.carbohydrateG()).param("fat", request.fatG()).param("now", now).update();
        }
        UUID finalGoalId = goalId;
        return getGoals(subject).stream().filter(goal -> goal.id().equals(finalGoalId)).findFirst().orElseThrow();
    }

    private java.util.Optional<UserProfileResponse> profile(String subject) {
        return jdbc.sql("SELECT id,display_name,timezone,locale,version FROM user_profile WHERE keycloak_sub=:subject AND status='ACTIVE'")
                .param("subject", subject).query((rs, row) -> new UserProfileResponse(rs.getObject("id", UUID.class),
                        rs.getString("display_name"), rs.getString("timezone"), rs.getString("locale"), rs.getLong("version"))).optional();
    }

    private UUID profileId(String subject) { return getProfile(subject).id(); }
    private List<String> stringList(String sql, UUID id) { return jdbc.sql(sql).param("id", id).query(String.class).list(); }
    private List<UUID> uuidList(String sql, UUID id) { return jdbc.sql(sql).param("id", id).query(UUID.class).list(); }
    private List<String> distinctUpper(List<String> source) {
        return source.stream().map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)).stream().sorted().toList();
    }
}
