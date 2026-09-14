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

import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    @Inject
    abstract protected ExecOperations getExecOperations();

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
        authMethods.add(new VaultRoleAndSecretID(roleId, secretId, VaultRoleAndSecretID.DEFAULT_VAULT_AUTH_PATH));
    }

    public void roleAndSecretEnv(String roleId, String secretId, String authPath) {
        authMethods.add(new VaultRoleAndSecretID(roleId, secretId, authPath));
    }

    @SuppressWarnings("unused")
    public void roleAndSecretEnv() {
        roleAndSecretEnv("VAULT_ROLE_ID", "VAULT_SECRET_ID", "VAULT_AUTH_PATH");
    }
    public class VaultRoleAndSecretID implements VaultAuthMethod {
        public static final String DEFAULT_VAULT_AUTH_PATH = "approle";

        private final String roleIdName;
        private final String secretIdName;
        private final String authPathName;

        public VaultRoleAndSecretID(String roleIdName, String secretIdName, String authPathName) {
            this.roleIdName = roleIdName;
            this.secretIdName = secretIdName;
            this.authPathName= authPathName;
        }

        public Provider<String> getRoleId() {
            return getProviderFactory().environmentVariable(roleIdName);
        }

        public Provider<String> getSecretId() {
            return getProviderFactory().environmentVariable(secretIdName);
        }

        public Provider<String> getAuthPath() {
            return getProviderFactory().environmentVariable(authPathName);
        }

        @Override
        public boolean isMethodUsable() {
            return getRoleId().isPresent() && getSecretId().isPresent();
        }

        @Override
        public String getExplanation() {
            if (getRoleId().isPresent()) {
                if (getSecretId().isPresent()) {
                    return "Using environment variable `" + roleIdName + "` for role id and `" + secretIdName + "` for secret id (for auth path `" + authPathName + "`)";
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
                    return "Read github token from " + ghTokenPath;
                } else {
                    return "Tried to read github token from " + ghTokenPath + " but the file is not readable.";
                }
            } else {
                return "Tried to read github token from " + ghTokenPath + " but the file does not exist.";
            }
        }
    }

    @SuppressWarnings("unused")
    public void ghCli() {
        authMethods.add(new GithubCli("gh"));
    }

    public class GithubCli implements VaultAuthMethod {

        private final String executable;
        private GithubCliResult result;

        GithubCli(String executable) {
            this.executable = executable;
        }

        public Provider<String> getToken() {
            return getProviderFactory().provider(() -> {
                final GithubCliResult currentResult = getResult();
                if (currentResult.token() == null) {
                    throw new GradleException(currentResult.explanation());
                }
                return currentResult.token();
            });
        }

        @Override
        public boolean isMethodUsable() {
            return getResult().token() != null;
        }

        @Override
        public String getExplanation() {
            return getResult().explanation();
        }

        private synchronized GithubCliResult getResult() {
            if (result == null) {
                result = runGhAuthToken();
            }
            return result;
        }

        private GithubCliResult runGhAuthToken() {
            final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
            try {
                final ExecResult execResult = getExecOperations().exec(spec -> {
                    spec.commandLine(executable, "auth", "token");
                    spec.setStandardOutput(standardOutput);
                    spec.setErrorOutput(OutputStream.nullOutputStream());
                    spec.setIgnoreExitValue(true);
                });
                if (execResult.getExitValue() != 0) {
                    return new GithubCliResult(
                            null,
                            "Tried to obtain a GitHub token using `gh auth token`, but the command exited with code "
                                    + execResult.getExitValue()
                    );
                }
                final String token = standardOutput.toString(StandardCharsets.UTF_8).trim();
                if (token.isEmpty()) {
                    return new GithubCliResult(
                            null,
                            "Tried to obtain a GitHub token using `gh auth token`, but the command returned an empty token"
                    );
                }
                return new GithubCliResult(token, "Using GitHub token from `gh auth token`");
            } catch (GradleException e) {
                return new GithubCliResult(
                        null,
                        "Tried to obtain a GitHub token using `gh auth token`, but the command could not be started"
                );
            }
        }
    }

    private record GithubCliResult(String token, String explanation) {}

}
