CREATE TABLE user_profile (
    id UUID PRIMARY KEY,
    keycloak_sub VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT user_profile_display_name_not_blank CHECK (CHAR_LENGTH(TRIM(display_name)) BETWEEN 1 AND 80)
);

CREATE TABLE dietary_preference (
    profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    PRIMARY KEY (profile_id, code)
);

CREATE TABLE allergy (
    profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    allergen_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (profile_id, allergen_code)
);

CREATE TABLE excluded_ingredient (
    profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    ingredient_reference_id UUID NOT NULL,
    PRIMARY KEY (profile_id, ingredient_reference_id)
);

CREATE TABLE nutrition_goal (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    kcal NUMERIC(8,2),
    protein_g NUMERIC(8,2),
    carbohydrate_g NUMERIC(8,2),
    fat_g NUMERIC(8,2),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT nutrition_goal_dates_valid CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT nutrition_goal_values_non_negative CHECK ((kcal IS NULL OR kcal >= 0) AND (protein_g IS NULL OR protein_g >= 0) AND (carbohydrate_g IS NULL OR carbohydrate_g >= 0) AND (fat_g IS NULL OR fat_g >= 0)),
    CONSTRAINT nutrition_goal_has_a_target CHECK (kcal IS NOT NULL OR protein_g IS NOT NULL OR carbohydrate_g IS NOT NULL OR fat_g IS NOT NULL)
);

CREATE UNIQUE INDEX nutrition_goal_profile_valid_from_unique ON nutrition_goal(profile_id, valid_from);
