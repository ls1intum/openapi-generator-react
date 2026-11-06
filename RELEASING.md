# Releasing

The generator is published to **Maven Central** as `de.tum.cit.aet:openapi-generator-react`, so consumers resolve it
from a plain `mavenCentral()` repository without authentication. It is released like
[`openapi-generator-angular22`](https://github.com/ls1intum/openapi-generator-angular22/blob/main/RELEASING.md), and
uses the same `de.tum.cit.aet` namespace on the Central Portal and the shared `ls1intum` GPG key.

**Status:** nothing has been published yet, and the publish job in
[`.github/workflows/build.yml`](.github/workflows/build.yml) has never run. Check it with the first release.

## One-time setup

1. Make sure the namespace `de.tum.cit.aet` is verified on <https://central.sonatype.com> (it is, if
   `openapi-generator-angular22` is published from the same account).
2. Add the repository secrets `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`: a user token of the Central Portal
   (Account, Generate User Token), not the login password.
3. Let the repository use the organization secrets `GPG_KEY` and `GPG_PASSPHRASE` (Organization settings, Secrets and
   variables, Actions, Repository access).

## Cutting a release

1. Set `version` in [`build.gradle.kts`](build.gradle.kts) and the versions in [`README.md`](README.md), and move the
   entries of [`CHANGELOG.md`](CHANGELOG.md) under the new version. Use a version without `SNAPSHOT`.
2. Merge to `main`, then tag and push:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. The `publish` job signs and uploads the artifacts to the Central Portal, releases them once they validate, and
   creates a GitHub release. The artifact appears on Maven Central after 15 to 30 minutes.

## Trying a build without releasing

`./gradlew publishToMavenLocal` installs the generator into `~/.m2` and needs no signing key. A consumer adds
`mavenLocal()` to its repositories to use it.
