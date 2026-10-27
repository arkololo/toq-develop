package fr.toq.user.api;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
        super("Créez votre profil TOQ avant de consulter ces données.");
    }
}
