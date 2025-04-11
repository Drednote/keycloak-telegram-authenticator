package io.github.drednote.keycloak_telegram_authenticator.provider;

import io.github.drednote.keycloak_telegram_authenticator.Constants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.services.ui.extend.UiTabProvider;
import org.keycloak.services.ui.extend.UiTabProviderFactory;

public class AttributesUiTabProvider implements UiTabProvider, UiTabProviderFactory<ComponentModel> {

    private static final Logger log = Logger.getLogger(AttributesUiTabProvider.class);

    @Override
    public String getId() {
        return "Attributes";
    }

    @Override
    public String getHelpText() {
        return null;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        realm.setAttribute(Constants.TELEGRAM_BOT_TOKEN, model.get(Constants.TELEGRAM_BOT_TOKEN));
        realm.setAttribute(Constants.TELEGRAM_AUTH_TIME_DELTA, model.get(Constants.TELEGRAM_AUTH_TIME_DELTA));
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel model) {
        realm.setAttribute(Constants.TELEGRAM_BOT_TOKEN, model.get(Constants.TELEGRAM_BOT_TOKEN));
        realm.setAttribute(Constants.TELEGRAM_AUTH_TIME_DELTA, model.get(Constants.TELEGRAM_AUTH_TIME_DELTA));
    }

    @Override
    public void preRemove(KeycloakSession session, RealmModel realm, ComponentModel model) {
        realm.removeAttribute(Constants.TELEGRAM_BOT_TOKEN);
        realm.removeAttribute(Constants.TELEGRAM_AUTH_TIME_DELTA);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        final ProviderConfigurationBuilder builder = ProviderConfigurationBuilder.create();
        builder.property()
            .name(Constants.TELEGRAM_BOT_TOKEN)
            .label("Set a Telegram Bot Token")
            .helpText("This token will be used for verified init_data from telegram")
            .type(ProviderConfigProperty.STRING_TYPE)
            .required(true)
            .secret(true)
            .add()

            .property()
            .name(Constants.TELEGRAM_AUTH_TIME_DELTA)
            .label("Set a Telegram Auth Time Delta (in seconds)")
            .required(false)
            .helpText("This parameter will be used for verified auth date of init_data from telegram. "
                      + "If empty, no verify will be")
            .type(ProviderConfigProperty.STRING_TYPE)
            .add()
        ;
        return builder.build();
    }

    @Override
    public String getPath() {
        return "/:realm/realm-settings/:tab?";
    }

    @Override
    public Map<String, String> getParams() {
        Map<String, String> params = new HashMap<>();
        params.put("tab", "attributes");
        return params;
    }
}