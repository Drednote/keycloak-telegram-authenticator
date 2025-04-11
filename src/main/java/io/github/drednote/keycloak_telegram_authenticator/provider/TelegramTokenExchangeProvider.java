package io.github.drednote.keycloak_telegram_authenticator.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.drednote.keycloak_telegram_authenticator.Constants;
import io.github.drednote.keycloak_telegram_authenticator.TelegramAuthValidator;
import io.github.drednote.keycloak_telegram_authenticator.TelegramAuthValidator.AuthParams;
import io.github.drednote.keycloak_telegram_authenticator.TelegramAuthValidator.AuthResponse;
import io.github.drednote.keycloak_telegram_authenticator.TelegramUser;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.OAuthErrorException;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.UserSessionModel.SessionPersistenceState;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenExchangeContext;
import org.keycloak.protocol.oidc.TokenExchangeProvider;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.protocol.oidc.TokenManager.AccessTokenResponseBuilder;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.CorsErrorResponseException;
import org.keycloak.services.Urls;
import org.keycloak.services.cors.Cors;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.utils.MediaType;

public class TelegramTokenExchangeProvider implements TokenExchangeProvider {

    private static final Logger log = Logger.getLogger(TelegramTokenExchangeProvider.class);
    private static final String INIT_DATA = "init_data";
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private static final String USER = "user";

    @Override
    public boolean supports(TokenExchangeContext context) {
        HttpRequest httpRequest = context.getSession().getContext().getHttpRequest();
        MultivaluedMap<String, String> params = httpRequest.getDecodedFormParameters();
        String initData = params.getFirst(INIT_DATA);
        return initData != null;
    }

    @Override
    public Response exchange(TokenExchangeContext context) {
        EventBuilder event = context.getEvent();
        KeycloakSession session = context.getSession();
        Cors cors = context.getCors();
        TokenExchangeContext.Params params = context.getParams();
        String audience = params.getAudience();
        String scope = params.getScope();

        AuthResponse authResponse = parseInitData(session, event, cors);
        UserModel userModel = getOrCreateUser(context, authResponse);
        AccessTokenResponseBuilder responseBuilder = generateTokens(context, userModel);

        AccessToken newToken = responseBuilder.getAccessToken();
        appendScope(newToken, scope);
        newToken.setOtherClaims(Constants.TELEGRAM_ID, authResponse.user().getId().toString());

        if (audience != null) {
            newToken.audience(audience);
            event.detail(Details.AUDIENCE, audience);
        }

        AccessTokenResponse response = responseBuilder.build();

        event.success();

        return cors.add(Response.ok(response, MediaType.APPLICATION_JSON_TYPE));
    }

    private void appendScope(AccessToken newToken, String scope) {
        StringBuilder newScope = new StringBuilder(newToken.getScope());
        if (scope != null) {
            newScope.append(" ").append(scope);
        }
        newScope.append(" ").append(Constants.TELEGRAM);
        newToken.setScope(newScope.toString());
    }

    private AccessTokenResponseBuilder generateTokens(
        TokenExchangeContext context, UserModel userModel
    ) {
        EventBuilder event = context.getEvent();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        ClientConnection clientConnection = context.getClientConnection();
        TokenManager tokenManager = (TokenManager) context.getTokenManager();
        ClientModel client = context.getClient();
        Map<String, String> clientAuthAttributes = context.getClientAuthAttributes();

        // Generate target token
        UserSessionModel userSession = session.sessions()
            .createUserSession(null, realm, userModel, userModel.getUsername(), clientConnection.getRemoteAddr(),
                "impersonate", false,
                null, null, SessionPersistenceState.PERSISTENT);
        RootAuthenticationSessionModel rootAuthSession = new AuthenticationSessionManager(
            session).createAuthenticationSession(realm, false);
        AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(client);

        authSession.setAuthenticatedUser(userModel);
        authSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        authSession.setClientNote(OIDCLoginProtocol.ISSUER,
            Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName()));
        authSession.setClientNote(OIDCLoginProtocol.SCOPE_PARAM, context.getParams().getScope());
        authSession.setClientScopes(Set.of());

        event.session(userSession);

        ClientSessionContext clientSessionCtx =
            TokenManager.attachAuthenticationSession(session, userSession, authSession);

        updateUserSessionFromClientAuth(userSession, clientAuthAttributes);

        return tokenManager
            .responseBuilder(realm, client, event, session, userSession, clientSessionCtx)
            .generateAccessToken()
            .generateRefreshToken();
    }

    private static AuthResponse parseInitData(KeycloakSession session, EventBuilder event, Cors cors) {
        KeycloakContext keycloakContext = session.getContext();
        MultivaluedMap<String, String> formParameters = keycloakContext.getHttpRequest().getDecodedFormParameters();
        String initData = formParameters.getFirst(INIT_DATA);

        String botToken = keycloakContext.getRealm().getAttribute(Constants.TELEGRAM_BOT_TOKEN);
        if (botToken == null) {
            event.detail(Details.REASON, "telegram_bot_token realm attribute is required");
            event.error("invalid_attribute");
            throw new CorsErrorResponseException(cors, "invalid_attribute",
                "telegram_bot_token realm attribute is required",
                Status.BAD_REQUEST);
        }

        AuthResponse authResponse;
        try {
            String authDeltaString = keycloakContext.getRealm().getAttribute(Constants.TELEGRAM_AUTH_TIME_DELTA);
            int authDelta = 0;
            if (authDeltaString != null) {
                authDelta = Integer.parseInt(authDeltaString);
            }
            authResponse = TelegramAuthValidator.validateData(
                new AuthParams(initData, botToken, authDelta > 0, authDelta));
        } catch (Exception e) {
            log.error("Unable to parse init_data", e);
            event.detail(Details.REASON, "Unable to parse init_data");
            event.error(Errors.INVALID_TOKEN);
            throw new CorsErrorResponseException(cors, OAuthErrorException.INVALID_TOKEN, "Unable to parse init_data",
                Status.BAD_REQUEST);
        }

        if (!authResponse.hashValid()) {
            event.detail(Details.REASON, "Failed to verify init_data");
            event.error(Errors.ACCESS_DENIED);
            throw new CorsErrorResponseException(cors, OAuthErrorException.ACCESS_DENIED, "Failed to verify init_data",
                Status.FORBIDDEN);
        } else if (!authResponse.dateValid()) {
            event.detail(Details.REASON, "Failed to verify init_data date");
            event.error(Errors.ACCESS_DENIED);
            throw new CorsErrorResponseException(cors, OAuthErrorException.ACCESS_DENIED,
                "Failed to verify init_data date", Status.FORBIDDEN);
        }
        return authResponse;
    }

    private void updateUserSessionFromClientAuth(
        UserSessionModel userSession, Map<String, String> clientAuthAttributes
    ) {
        for (Map.Entry<String, String> attr : clientAuthAttributes.entrySet()) {
            userSession.setNote(attr.getKey(), attr.getValue());
        }
    }

    private UserModel getOrCreateUser(TokenExchangeContext context, AuthResponse authResponse) {
        UserProvider userProvider = context.getSession().users();
        return userProvider.searchForUserByUserAttributeStream(context.getRealm(), Constants.TELEGRAM_ID,
                authResponse.user().getId().toString())
            .findFirst()
            .orElseGet(() -> createNewUser(context, authResponse));
    }

    private UserModel createNewUser(TokenExchangeContext context, AuthResponse authResponse) {
        EventBuilder event = context.getEvent();
        RealmModel realm = context.getRealm();
        Cors cors = context.getCors();
        UserProvider userProvider = context.getSession().users();
        TelegramUser user = authResponse.user();

        if (!realm.isRegistrationAllowed()) {
            event.detail(Details.REASON,
                "Can't create new telegram authenticated user! User registration is not allowed.");
            event.error(Errors.INVALID_CONFIG);
            throw new CorsErrorResponseException(cors, OAuthErrorException.ACCESS_DENIED,
                "Can't create new telegram authenticated user! User registration is not allowed.", Status.BAD_REQUEST);
        }

        UserModel userModel = userProvider.addUser(realm, user.getUserName());
        userModel.setEnabled(true);
        userModel.setFirstName(user.getFirstName());
        userModel.setLastName(user.getLastName());
        userModel.setSingleAttribute(Constants.TELEGRAM_ID, user.getId().toString());

        try {
            Map<String, String> userFields = objectMapper
                .readValue(authResponse.data().get(USER), new TypeReference<>() {});
            userFields.forEach((key, value) -> {
                if (value != null) {
                    userModel.setSingleAttribute(Constants.TELEGRAM + "_" + key, value);
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Cannot set attributes for user", e);
        }
        return userModel;
    }

    @Override
    public void close() {

    }
}
