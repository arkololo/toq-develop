package fr.toq.user.api;

import fr.toq.user.application.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/display-names")
public class DisplayNameController {
    private final UserProfileService profiles;
    public DisplayNameController(UserProfileService profiles) { this.profiles = profiles; }
    @PostMapping
    public Map<String, String> list(@Valid @RequestBody @Size(max = 100) List<@NotBlank @Size(max = 255) String> subjects) {
        return profiles.displayNames(subjects.stream().distinct().toList());
    }
}
