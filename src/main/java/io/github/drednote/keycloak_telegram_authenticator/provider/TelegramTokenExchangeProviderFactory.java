package io.github.drednote.keycloak_telegram_authenticator.provider;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.oidc.TokenExchangeProvider;
import org.keycloak.protocol.oidc.TokenExchangeProviderFactory;

public class TelegramTokenExchangeProviderFactory implements TokenExchangeProviderFactory {

    @Override
    public TokenExchangeProvider create(KeycloakSession session) {
        return new TelegramTokenExchangeProvider();
    }

    @Override
    public void init(Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "Telegram Auth Token Exchange";
    }

    @Override
    public int order() {
        return 10;
    }
}
