package co.elastic.gradle.vault;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract public class VaultAuthenticationExtension {

    public interface VaultAuthMethod {

        boolean isMethodUsable();

        String getExplanation();

    }

    private final List<VaultAuthMethod> authMethods = new ArrayList<>();

    public List<VaultAuthMethod> getAuthMethods() {
        return Collections.unmodifiableList(authMethods);
    }

    @Inject
    abstract protected ProviderFactory getProviderFactory();

    @SuppressWarnings("unused")
    public void tokenFile(File path) {
        authMethods.add(new VaultTokenFile(path.toPath()));
    }

    public VaultTokenFile internalCachedTokenFile(File path) {
        return new VaultTokenFile(path.toPath());
    }

    public class VaultTokenFile implements VaultAuthMethod{
        private final Path vaultTokenPath;

        public VaultTokenFile(Path vaultTokenPath) {
            this.vaultTokenPath = vaultTokenPath;
        }

        public Provider<String> getToken() {
            return getProviderFactory().provider(() -> Files.readString(vaultTokenPath).trim());
        }

        @Override
        public boolean isMethodUsable() {
            return Files.isReadable(vaultTokenPath);
        }

        @Override
        public String getExplanation() {
            if (Files.exists(vaultTokenPath)) {
                if (Files.isReadable(vaultTokenPath)) {
                    return "Reading a Vault token from file: " + vaultTokenPath;
                } else {
                    return "Tried to read a vault token from file, but it's not readable: " + vaultTokenPath;
                }
            } else {
                return "Tried to read a vault token from file, but it doesn't exist:" + vaultTokenPath.toAbsolutePath();
            }
        }


    }

    public void tokenEnv(String name) {
        authMethods.add(new VaultTokenEnvVar(name));
    }
    @SuppressWarnings("unused")
    public void tokenEnv() {
        tokenEnv("VAULT_TOKEN");
    }
    public class VaultTokenEnvVar implements VaultAuthMethod {
        private final String envVarName;

        public VaultTokenEnvVar(String envVarName) {
            this.envVarName = envVarName;
        }

        public Provider<String> getToken() {
            return getProviderFactory().environmentVariable(envVarName);
        }

        @Override
        public boolean isMethodUsable() {
            return getToken().isPresent();
        }

        @Override
        public String getExplanation() {
            if (isMethodUsable()) {
                return "Using a vault token from environment variable " + envVarName;
            } else {
                return "Tried using a token from environment variable " + envVarName + " but no such variable is defined.";
            }
        }
    }


    public void roleAndSecretEnv(String roleId, String secretId) {
        authMethods.add(new VaultRoleAndSecretID(roleId, secretId));
    }
    @SuppressWarnings("unused")
    public void roleAndSecretEnv() {
        roleAndSecretEnv("VAULT_ROLE_ID", "VAULT_SECRET_ID");
    }
    public class VaultRoleAndSecretID implements VaultAuthMethod {

        private final String roleIdName;
        private final String secretIdName;

        public VaultRoleAndSecretID(String roleIdName, String secretIdName) {
            this.roleIdName = roleIdName;
            this.secretIdName = secretIdName;
        }

        public Provider<String> getRoleId() {
            return getProviderFactory().environmentVariable(roleIdName);
        }

        public Provider<String> getSecretId() {
            return getProviderFactory().environmentVariable(secretIdName);
        }

        @Override
        public boolean isMethodUsable() {
            return getRoleId().isPresent() && getSecretId().isPresent();
        }

        @Override
        public String getExplanation() {
            if (getRoleId().isPresent()) {
                if (getSecretId().isPresent()) {
                    return "Using environment variable `" + roleIdName + "` for role id and `" + secretIdName + "` for secret id" ;
                } else {
                    return "Tried to use environment variable `" + roleIdName + "` for role id, but environment variable `" + secretIdName + "` for secret id is not defined" ;
                }
            } else {
                if (getSecretId().isPresent()) {
                    return "Tried to use environment variable `" + secretIdName + "` for secret id, but environment variable `" + roleIdName + "` for role id is not defined" ;
                } else {
                    return "Tried to use environment variable `" + roleIdName + "` for role id, and environment variable `" + secretIdName + "` but neither are defined";
                }
            }
        }
    }

    public void ghTokenEnv(String name) {
        authMethods.add(new GithubTokenEnv(name));
    }
    @SuppressWarnings("unused")
    public void ghTokenEnv() {
        ghTokenEnv("VAULT_AUTH_GITHUB_TOKEN");
    }
    public class GithubTokenEnv implements VaultAuthMethod {

        private final String envVarName;

        public GithubTokenEnv(String envVarName) {
            this.envVarName = envVarName;
        }

        public Provider<String> getToken() {
            return getProviderFactory().environmentVariable(envVarName);
        }

        @Override
        public boolean isMethodUsable() {
            return getToken().isPresent();
        }

        @Override
        public String getExplanation() {
            if (isMethodUsable()) {
                return "Using GitHub token from environment variable `" + envVarName + "`";
            } else {
                return "Tried using GitHub token from environment variable `" + envVarName + "` but it was not defined";
            }
        }
    }

    public void ghTokenFile(File path) {
        authMethods.add(new GithubTokenFile(path.toPath()));
    }
    @SuppressWarnings("unused")
    public void ghTokenFile() {
        ghTokenFile(new File(getProviderFactory().systemProperty("user.home").get() + "/.elastic/github.token"));
    }
    public class GithubTokenFile implements VaultAuthMethod {

        private final Path ghTokenPath;

        public GithubTokenFile(Path ghTokenPath) {
            this.ghTokenPath = ghTokenPath;
        }

        public Provider<String> getToken() {
            return getProviderFactory().provider(() -> Files.readString(ghTokenPath).trim());
        }

        @Override
        public boolean isMethodUsable() {
            return Files.isReadable(ghTokenPath);
        }

        @Override
        public String getExplanation() {
            if (Files.exists(ghTokenPath)) {
                if (Files.isReadable(ghTokenPath)) {
                    return "Red github token from " + ghTokenPath;
                } else {
                    return "Tried to read github token from " + ghTokenPath + " but the file is not readable.";
                }
            } else {
                return "Tried to read github token from " + ghTokenPath + " but the file does not exist.";
            }
        }
    }

}
