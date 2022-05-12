Gradle Vault Plugin
===================

About
-----

This plugin provides build scripts access to secrets stored in [vault](https://www.vaultproject.io/). These secrets can 
provide access to anything from repository credentials, to remote build cache credentials, to API access keys to various 
services. Users of the build script will only have to provide credentials to be able to authenticate to vault, or a vault 
token that will provide access to the required secrets.  

Design goals 
------------

There are a couple of alternate Gradle Vault plugins. We implemented our own for the following principles: 
- provide access to secrets early including in Gradle settings
- provide fast access to secrets and ability to cache them, so that we can use them at configuration time. Some 
  configuration in Gradle e.g. repositories or remote build cache needs to happen early on, so we need a mechanism that 
  is fast enough to be used at configuration time too.    
- modern implementation based on [lazy properties](https://docs.gradle.org/current/userguide/lazy_configuration.html#lazy_properties)
- dedicated support for Gradle multi-project support. Cached secrets work across multiple projects.

Usage
-----

From `settings.gradle.kts`
```kotlin
plugins {
   id("co.elastic.vault-settings")
}
configure<VaultExtension> {
  address.set("http://example.com/")
  auth {
    tokenEnv() // Will look for a vault token in the VAULT_TOKEN environmental variable
    tokenEnv("MY_ENV_TOKEN")
    tokenFile(file("path/to/token"))
    roleAndSecretEnv("ROLEID", "SECRETID")
    roleAndSecretEnv() // Will look for a vault role and secret id in the VAULT_ROLE_ID and VAULT_SECRET_ID environmental variables 
    ghTokenEnv("SOME_GH_TOKEN")
    ghTokenEnv() // Will look for a GitHub Token in the VAULT_AUTH_GITHUB_TOKEN environment variable
    ghTokenFile(file("theres/no/such/file"))
    ghTokenFile() // Will look for a GitHub Token in the ~/.elastic/github.token file
  }
}
val vault = the<VaultExtension>()                  
logger.lifecycle("db_password is {}", vault.readAndCacheSecret("secret/testing").get()["db_password"])
logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
```

From `build.gradle.kts` the same configuration is available but the extension is available directly:
```kotlin
plugins {
   id("co.elastic.vault")
}
vault {
  // same configuration available here 
}
logger.lifecycle("db_password is {}", vault.readAndCacheSecret("secret/testing").get()["db_password"])
logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
```

#### `readSecret`

Returns a [Provider<Map<String,String>>](https://docs.gradle.org/current/javadoc/org/gradle/api/provider/Provider.html) 
with keys and values populated with the secrets from vault.  

### Authentication

The `auth {` section supports multiple ways of accessing vault. Multiple ways can be configured in which case they will 
be tried in the order in which they were defined. If an authentication method is available it will we used, 
the plugin checks for the existence of the files or environmental variables only and expects that these will work if 
present. This can be used to authenticate to vault differently in different contexts (e.g. GH token could be used locally
and role and secret id in CI).

### `tokenEnv()` or `tokenEnv(String name)`

Accesses vault directly with the token provided in the named environment variable (`VAULT_TOKEN` by default).

### `tokenFile(File path)`

Access vault directly with the token red from the content of the file passed as a paramter. 

### `roleAndSecretEnv()` and `roleAndSecretEnv(String role_id, Strin secret_id)` 

Authenticates to vault using the role and secret ids by the named environment variables 
(`VAULT_ROLE_ID` and `VAULT_SECRET_ID` by default).
A vault token is stored in the local filesystem in a file readable only by the current user and re-used while it's not 
expired to speed up future usages of the plugin. 

### `ghTokenEnv()` and `ghTokenEnv(String name)`

Authenticates to vault using the GitHub API token from the named environment variable (`VAULT_AUTH_GITHUB_TOKEN` by default).
Note that the GitHub token might require to be authenticated to specific organisations if the vault policies check for this.
A vault token is stored in the local filesystem in a file readable only by the current user and re-used while it's not
expired to speed up future usages of the plugin.

### `ghTokenFile()` and `ghTokenFile(File path)`

Authenticates to vault using the GitHub API token from the contents of the file passed as a parameter 
(`~/.elastic/github.token` by default).
Note that the GitHub token might require to be authenticated to specific organisations if the vault policies check for this.
A vault token is stored in the local filesystem in a file readable only by the current user and re-used while it's not
expired to speed up future usages of the plugin.

### Configuration

```kotlin
vault {
  address.set("http://example.com/")
  engineVersion.set(2)
  retries.set(2)
  retryDelayMillis.set(1000)
}
```

#### address

The only mandatory parameter. Configures the URL used to connect to vault.

#### engineVersion

Configure THe API version of the KV engine. This defaults to `1`. At the time of this writing the other available option
is `2`. This is a server specific configuration option. Consider changing it if you're not able to access secrets that 
you should be able to.   

#### retries and retryDelayMillis

Configures how many times to re-try connecting to vault and the delay between these tries.

### Checking if vault is available 

Sometimes one might want to fall back gracefully if no vault authentication is available. This can be used for example 
to allow folks that don't have access to all the vault secrets to still build at least parts of the project.
The `vault` extensions provides the `isAuthAvailable()` method for this purpose.  

In the following example, the secret from vault will only be red if vault authentication is configured as otherwise the
task will be skipped.
```kotlin
tasks.register("someTaskThatRequiresSecrets") {
  doFirst {
    logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
  }
  onlyIf { vault.isAuthAvailable }
}
```

# Troubleshooting 

## Secret not available at the expected path 

The plugin will raise an exception if vault doesn't provide any data for a requested path. 
When this happens, check the following: 
   - the path is actually present in vault, and you can access it. You can use the vault cli authenticated in the same way.
   - the `engineVersion` property of the vault extension is configured to match what your vault server supports. 
   - if using a GitHub token make sure it's authorised for organisations your vault server might care of. Vault can be 
     configured to take GitHub teams you are member of into account when assigning policies which might only be visible   
     if the GitHub token is authorised for the organisations that define those teams.