package co.elastic.gradle.vault;

import co.elastic.gradle.vault.VaultAuthenticationExtension.VaultTokenFile;
import com.bettercloud.vault.Vault;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.*;

abstract public class VaultExtension {

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
            final LogicalResponse response = driver.logical().read(path);
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

        final Map<String, String> cachedData = tryReadCache(leaseExpiration, dataPath);
        if (cachedData != null) {
            return getProviderFactory().provider(() -> cachedData);
        }

        return getProviderFactory().provider(() -> {
            logger.lifecycle("Reading " + path + " from vault (cached value not available or expired)");
            final Vault driver = getDriver();
            final LogicalResponse response = driver.logical().read(path);
            final Map<String, String> data = response.getData();
            if (data.isEmpty()) {
                throw new GradleException("No data was available in vault path " + path);
            }

            writeCacheDir(
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
            data.forEach((key, value) -> {
                writeCacheDir(dataPath.resolve(key), value);
            });

            return data;
        });
    }

    private Map<String, String> tryReadCache(Path leaseExpiration, Path data) {
        if (Files.exists(leaseExpiration)) {
            try {
                final long expireMillis = Long.parseLong(Files.readString(leaseExpiration));
                if (isValidLease(expireMillis)) {
                    return Files.list(data).collect(Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> {
                                try {
                                    return Files.readString(path);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                    ));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    private void writeCacheDir(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(
                        path,
                        PosixFilePermissions.fromString("rw-------")
                );
            } else {
                logger.warn("Not able to set {} to read only because the filesystem is not posix.", path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        if (cachedToken.isMethodUsable() && isValidLease(expiration)) {
            return (new VaultAccessStrategy()).access(this, cachedToken, (token, expireMillis) -> {});
        }
        logger.lifecycle("Authenticating to vault at " + getAddress().get());
        for (VaultAuthenticationExtension.VaultAuthMethod authMethod : getAuthExtension().getAuthMethods()) {
            logger.lifecycle(authMethod.getExplanation());
            if (authMethod.isMethodUsable()) {
                return (new VaultAccessStrategy()).access(this, authMethod, (token, expireMillis) -> {
                    writeCacheDir(tokenValue, token);
                    writeCacheDir(cachedTokenExpiration, expireMillis.toString());
                });
            }
        }
        throw new GradleException("Could not find a suitable auth strategy");
    }

    private boolean isValidLease(Long expirationMillis) {
        return expirationMillis - EXPIRATION_BUFFER > System.currentTimeMillis();
    }
}
