Applies the following conventions:

- applies the [lifecycle plugin](../lifecycle/README.md)
- configures the vault address and a common authentication scheme
- configures Develocity build scans and its remote Gradle build cache
- cli plugins and snyk api token

The remote cache at `https://gradle-enterprise.elastic.co` is enabled for all builds.
Local builds read from it but do not push; Jenkins and Buildkite builds both read and push.
Develocity reads its access key from the standard `DEVELOCITY_ACCESS_KEY` environment
variable when one is provided. This repository's bootstrap pipeline injects that variable
because it cannot rely on the conventions plugin before building it.

In other Jenkins and Buildkite pipelines, the conventions plugin reads the `accesskey`
field from the shared KV v2 secret
`kv/ci-shared/develocity/gradle-build-cache-access-key`. Each consuming repository must
be granted read access to `kv/ci-shared/develocity/*` in Terrazzo. Configuration fails
with a grant-specific error if a CI build has neither the environment variable nor access
to the shared Vault secret.
