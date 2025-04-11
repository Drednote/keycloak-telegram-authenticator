# keycloak-telegram-authenticator

## Contents

* [Description](#description)
* [Quick Start](#quick-start)
* [Manual Installation](#manual-installation)
* [License](#license)
* [Contributing](#contributing)

## Description

This project enables authorization and retrieval of a user token in Keycloak using data from Telegram. You can learn more about Telegram authorization [here](https://docs.telegram-mini-apps.com/platform/init-data). The `init_data` verification algorithm replaces the user's password input.

This project implements token retrieval only through direct access using `grant_type` with the `token-exchange` type. For UI-based authorization, I recommend using another project: [telegram-web-keycloak-authenticator](https://github.com/rickispp/telegram-web-keycloak-authenticator).

## Quick Start

### Requirements

- Java 21 or higher installed
- Docker and docker-compose
- You have a Telegram bot, and you know its username and token

### Install

For a quick start, you can deploy the code from this repository using `docker-compose`. This will set up Keycloak with this plugin.

1. Clone this repository:
   ```shell
   git clone https://github.com/Drednote/telegram-keycloak-authenticator
   ```  

2. Build the project:
   ```shell
   ./gradlew build
   ```  

3. Run `docker-compose`:
   ```shell
   docker-compose up
   ```  

After deployment, you will need to:
- Create a realm and configure a client to obtain `client_id` and `client_secret` (you can keep the default settings, but ensure **Client Authentication** is enabled).
- Add the bot token to the realm attributes (used to validate `init_data`) and set the validity period for `init_data` (starting from the Telegram authorization time).
  - These settings can be configured in **Realm Settings → Attributes** (tab).
  > **Note:** If the required features tags are not added when starting Keycloak, the tab will not appear.


### Usage

Once everything is configured, you can test token retrieval with the following request:

- `grant_type` must be set to `urn:ietf:params:oauth:grant-type:token-exchange`.

```shell
KCHOST=http://localhost:8090  

ACCESS_TOKEN=$(curl \  
  -X POST \  
  -d "client_id=$CLIENT_ID" -d "client_secret=$CLIENT_SECRET" \  
  -d "init_data=$init_data" \  
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \  
  "$KCHOST/realms/$REALM/protocol/openid-connect/token") #| jq '.access_token')  
echo $ACCESS_TOKEN
```

- **Note**: To send `init_data` using curl, it must be converted to `json`. The example below shows what init_data should look like, but it is not valid because it is missing required fields.:
```shell
init_data="{\"user\":\"{\\\"first_name\\\":\\\"Ivan\\\"}\",\"chat_type\":\"private\",\"auth_date\":\"1744138538\"}"
```
- In normal use, `init_data` should look like what Telegram sends itself. 
```shell
init_data="user=%7B%22id%22%3A12345%2C%22first_name%22%3A%22John%22%7D&auth_date=1680000000"
```

## Manual Installation

### Requirements

- Keycloak 26 or higher

### Installation

To manually install the plugin:
1. **Download the plugin**:
    - Get the latest JAR from [Releases](https://github.com/Drednote/telegram-keycloak-authenticator/releases).
2. **Deploy to Keycloak**:
    - Copy the JAR into `/opt/keycloak/providers/` directory of your Keycloak instance.
3. **Enable required features**:
    - Start Keycloak with additional parameters:
      ```shell
      kc.[sh|bat] start --features=declarative-ui,token-exchange
      ```  
After deployment, you will need to:
- Create a realm and configure a client to obtain `client_id` and `client_secret` (you can keep the default settings, but ensure **Client Authentication** is enabled).
- Add the bot token to the realm attributes (used to validate `init_data`) and set the validity period for `init_data` (starting from the Telegram authorization time).
    - These settings can be configured in **Realm Settings → Attributes** (tab).
  > **Note:** If the required features tags are not added when starting Keycloak, the tab will not appear.

## License
This project is licensed under [MIT License](LICENSE).

## Contributing
Pull requests are welcome! For major changes, please open an issue first.  