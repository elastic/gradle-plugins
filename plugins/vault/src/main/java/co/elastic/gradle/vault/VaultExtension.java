/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.vault;

import co.elastic.gradle.utils.CacheUtils;
import co.elastic.gradle.vault.VaultAuthenticationExtension.VaultTokenFile;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

abstract public class VaultExtension implements ExtensionAware {

    private static final Logger logger = Logging.getLogger(VaultExtension.class);
    public static final long EXPIRATION_BUFFER = MILLISECONDS.convert(2, SECONDS);

    private final File cacheDir;

    public VaultExtension(File cacheDir) {
        this.cacheDir = cacheDir;
        getEngineVersion().convention(1);
        getRetries().convention(5);
        getRetryDelayMillis().convention(1000);
    }

    public abstract Property<String> getAddress();

    public abstract Property<Integer> getEngineVersion();

    public abstract Property<Integer> getRetries();

    public abstract Property<Integer> getRetryDelayMillis();

    @Inject
    abstract protected ProviderFactory getProviderFactory();

    @SuppressWarnings("unused")
    public void auth(Action<VaultAuthenticationExtension> spec) {
        spec.execute(getAuthExtension());
    }

    private VaultAuthenticationExtension getAuthExtension() {
        return ((ExtensionAware) this).getExtensions().getByType(VaultAuthenticationExtension.class);
    }

    public boolean isAuthAvailable() {
        return getAuthExtension().getAuthMethods().stream()
                .anyMatch(VaultAuthenticationExtension.VaultAuthMethod::isMethodUsable);
    }

    @SuppressWarnings("unused")
    public Provider<Map<String, String>> readSecret(String path) {
        return getProviderFactory().provider(() -> {
            logger.lifecycle("Reading " + path + " from vault");
            final Vault driver = getDriver();
            LogicalResponse response = getDataFromVault(path);
            final Map<String, String> data = response.getData();
            if (data.isEmpty()) {
                throw new GradleException("No data was available in vault path " + path);
            }
            return data;
        });
    }

    @SuppressWarnings("unused")
    public Provider<Map<String, String>> readAndCacheSecret(String path) {
        final Path leaseExpiration = cacheDir.toPath().resolve(path).resolve("leaseExpiration");
        final Path dataPath = cacheDir.toPath().resolve(path).resolve("data");

        final Map<String, String> cachedData = CacheUtils.tryReadCache(leaseExpiration, dataPath);
        if (cachedData != null) {
            return getProviderFactory().provider(() -> cachedData);
        }

        return getProviderFactory().provider(() -> {
            logger.lifecycle("Reading " + path + " from vault (cached value not available or expired)");

            LogicalResponse response = getDataFromVault(path);

            CacheUtils.writeCacheDir(
                    leaseExpiration,
                    String.valueOf(
                            System.currentTimeMillis() + MILLISECONDS.convert(
                                    (response.getLeaseDuration() == 0) ?
                                            SECONDS.convert(1, DAYS) :
                                            response.getLeaseDuration(),
                                    SECONDS
                            )
                    )
            );
            final Map<String, String> data = response.getData();
            data.forEach((key, value) -> {
                CacheUtils.writeCacheDir(dataPath.resolve(key), value);
            });

            return data;
        });
    }

    protected LogicalResponse getDataFromVault(String path) throws VaultException {
        final Vault driver = getDriver();
        LogicalResponse response = null;
        for (int retries = 0; retries < 5; retries++) {
            response = driver.logical().read(path);
            if (response.getData().isEmpty()) {
                if (retries == 4) { // Check if it's the last retry
                    throw new GradleException("No data was available in vault path " + path);
                }
            } else {
                break;
            }

            try {
                // Exponential back-off: 2^(retries + 2) * 500 milliseconds (e.g., 2000 ms, 4000 ms, 8000 ms, ...)
                long backOffTime = (long) Math.pow(2, retries + 2) * 500;
                TimeUnit.MILLISECONDS.sleep(backOffTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GradleException("Retry interrupted for vault path " + path, e);
            }
        }
        return response;
    }

    private Vault getDriver() {
        if (getAuthExtension().getAuthMethods().isEmpty()) {
            throw new GradleException("No authentication configured to access " + getAddress().get() +
                    "\nUse an `auth {}` block to configure at least one authentication method");
        }

        Path cachedTokenExpiration = cacheDir.toPath().resolve("token/expiration");
        final Path tokenValue = cacheDir.toPath().resolve("token/value");

        Long expiration = 0L;
        if (Files.exists(cachedTokenExpiration)) {
            try {
                expiration = Long.parseLong(Files.readString(cachedTokenExpiration));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        final VaultTokenFile cachedToken = getAuthExtension().internalCachedTokenFile(
                tokenValue.toFile()
        );
        if (cachedToken.isMethodUsable() && CacheUtils.isValidLease(expiration)) {
            return (new VaultAccessStrategy()).access(this, cachedToken, (token, expireMillis) -> {});
        }
        logger.lifecycle("Authenticating to vault at " + getAddress().get());
        for (VaultAuthenticationExtension.VaultAuthMethod authMethod : getAuthExtension().getAuthMethods()) {
            logger.lifecycle(authMethod.getExplanation());
            if (authMethod.isMethodUsable()) {
                return (new VaultAccessStrategy()).access(this, authMethod, (token, expireMillis) -> {
                    CacheUtils.writeCacheDir(tokenValue, token);
                    CacheUtils.writeCacheDir(cachedTokenExpiration, expireMillis.toString());
                });
            }
        }
        throw new GradleException("Could not find a suitable auth strategy");
    }
}
