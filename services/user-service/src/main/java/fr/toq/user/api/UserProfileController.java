package fr.toq.user.api;

import fr.toq.user.application.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {
    private final UserProfileService profiles;

    public UserProfileController(UserProfileService profiles) {
        this.profiles = profiles;
    }


    @GetMapping
    public UserProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return profiles.getProfile(jwt.getSubject());
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> putProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileRequest request) {
        UserProfileResponse profile = profiles.saveProfile(jwt, request);
        return ResponseEntity.status(HttpStatus.OK).eTag("\"" + profile.version() + "\"").body(profile);
    }

    @GetMapping("/preferences")
    public PreferencesResponse getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return profiles.getPreferences(jwt.getSubject());
    }

    @PutMapping("/preferences")
    public PreferencesResponse putPreferences(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PreferencesRequest request) {
        return profiles.savePreferences(jwt.getSubject(), request);
    }

    @GetMapping("/nutrition-goals")
    public List<NutritionGoalResponse> getGoals(@AuthenticationPrincipal Jwt jwt) {
        return profiles.getGoals(jwt.getSubject());
    }

    @PutMapping("/nutrition-goals")
    public NutritionGoalResponse putGoal(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NutritionGoalRequest request) {
        return profiles.saveGoal(jwt.getSubject(), request);
    }

    public record ProfileRequest(@NotBlank @Size(max = 80) String displayName) { }

    public record UserProfileResponse(UUID id, String displayName, String timezone, String locale, long version) { }

    public record PreferencesRequest(
            @NotNull List<@Pattern(regexp = "^[A-Z_]+$") @Size(max = 40) String> dietaryPreferenceCodes,
            @NotNull List<@Pattern(regexp = "^[A-Z_]+$") @Size(max = 40) String> allergyCodes,
            @NotNull List<UUID> excludedIngredientIds) { }

    public record PreferencesResponse(List<String> dietaryPreferenceCodes, List<String> allergyCodes,
            List<UUID> excludedIngredientIds) { }

    public record NutritionGoalRequest(
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            @DecimalMin("0.0") BigDecimal kcal,
            @DecimalMin("0.0") BigDecimal proteinG,
            @DecimalMin("0.0") BigDecimal carbohydrateG,
            @DecimalMin("0.0") BigDecimal fatG) { }

    public record NutritionGoalResponse(UUID id, LocalDate validFrom, LocalDate validTo, BigDecimal kcal,
            BigDecimal proteinG, BigDecimal carbohydrateG, BigDecimal fatG, long version) { }
}
