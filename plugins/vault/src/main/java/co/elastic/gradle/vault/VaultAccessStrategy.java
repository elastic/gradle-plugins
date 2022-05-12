package co.elastic.gradle.vault;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.AuthResponse;
import org.gradle.api.GradleException;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class VaultAccessStrategy {

    public Vault access(
            VaultExtension vault,
            VaultAuthenticationExtension.VaultAuthMethod authMethod,
            BiConsumer<String, Long> tokenAction
    ) {
        if (authMethod instanceof VaultAuthenticationExtension.VaultTokenEnvVar) {
            return authWithToken(vault, ((VaultAuthenticationExtension.VaultTokenEnvVar) authMethod).getToken().get());
        } else if (authMethod instanceof VaultAuthenticationExtension.VaultTokenFile) {
            return authWithToken(vault, ((VaultAuthenticationExtension.VaultTokenFile) authMethod).getToken().get());
        } else if (authMethod instanceof VaultAuthenticationExtension.VaultRoleAndSecretID) {
            return authAndStoreToken(vault, (driver) -> {
                        try {
                            final VaultAuthenticationExtension.VaultRoleAndSecretID roleAndSecret = (VaultAuthenticationExtension.VaultRoleAndSecretID) authMethod;
                            return driver.auth().loginByAppRole(
                                    roleAndSecret.getRoleId().get(),
                                    roleAndSecret.getSecretId().get()
                            );
                        } catch (VaultException e) {
                            throw new GradleException("Failed to authenticate to vault", e);
                        }
                    },
                    tokenAction
            );
        } else if (authMethod instanceof VaultAuthenticationExtension.GithubTokenFile) {
            return authAndStoreToken(vault, (driver) -> {
                        try {
                            return driver.auth().loginByGithub(((VaultAuthenticationExtension.GithubTokenFile) authMethod).getToken().get());
                        } catch (VaultException e) {
                            throw new GradleException("Failed to authenticate to vault", e);
                        }
                    },
                    tokenAction
            );
        } else if (authMethod instanceof VaultAuthenticationExtension.GithubTokenEnv) {
            return authAndStoreToken(vault, (driver) -> {
                        try {
                            return driver.auth().loginByGithub(((VaultAuthenticationExtension.GithubTokenEnv) authMethod).getToken().get());
                        } catch (VaultException e) {
                            throw new GradleException("Failed to authenticate to vault", e);
                        }
                    },
                    tokenAction
            );
        } else {
            throw new IllegalStateException("Unsupported auth method " + authMethod.getClass());
        }
    }

    private Vault authAndStoreToken(
            VaultExtension vault,
            Function<Vault, AuthResponse> authResponseSupplier,
            BiConsumer<String, Long> tokenAction) {
        try {
            final Vault driver = new Vault(
                    new VaultConfig()
                            .address(vault.getAddress().get())
                            .engineVersion(vault.getEngineVersion().get())
                            .build()
            ).withRetries(vault.getRetries().get(), vault.getRetryDelayMillis().get());
            final AuthResponse authResponse = authResponseSupplier.apply(driver);
            final String authClientToken = authResponse.getAuthClientToken();
            tokenAction.accept(
                    authClientToken,
                    System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(
                            authResponse.getAuthLeaseDuration(),
                            TimeUnit.SECONDS
                    )
            );
            return authWithToken(vault, authClientToken);
        } catch (VaultException e) {
            throw new GradleException("Failed to connect to vault", e);
        }
    }

    private Vault authWithToken(VaultExtension vault, String token) {
        try {
            return new Vault(
                    new VaultConfig()
                            .address(vault.getAddress().get())
                            .engineVersion(vault.getEngineVersion().get())
                            .token(token)
                            .build()
            )
                    .withRetries(vault.getRetries().get(), vault.getRetryDelayMillis().get());
        } catch (VaultException e) {
            throw new GradleException("Failed to connect to vault", e);
        }
    }
}
