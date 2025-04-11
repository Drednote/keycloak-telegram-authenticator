package io.github.drednote.keycloak_telegram_authenticator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class TelegramUser {

    @JsonProperty("id")
    @Nonnull
    private Long id;

    @JsonProperty("username")
    @Nonnull
    private String userName;

    @JsonProperty("first_name")
    @Nullable
    private String firstName;

    @JsonProperty("last_name")
    @Nullable
    private String lastName;
}
