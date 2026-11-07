# Maven Central releases

GitHub Actions builds and signs the libraries. To check packaging from a browser,
open **Actions → Publish → Run workflow**. The run executes Verify and uploads
an unsigned Maven repository. It works without Central credentials.

## One-time setup

1. In [Central Portal](https://central.sonatype.com/publishing/namespaces), check
   that your account can publish under `io.github.roman-n1` or the project
   namespace `io.github.roman-n1.mothlight`.
2. Add the following [Actions secrets for Mothlight](https://github.com/roman-n1/mothlight/settings/secrets/actions).
   Keep these values in GitHub and in your password manager.

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | Username from the [Central Portal user token](https://central.sonatype.com/usertoken) |
   | `MAVEN_CENTRAL_PASSWORD` | Password from the same token |
   | `MAVEN_GPG_KEY_CONTENTS` | ASCII-armored private signing key whose public key is available to Central |
   | `MAVEN_SIGNING_PASSWORD` | Passphrase, if the signing key is protected |
   | `MAVEN_SIGNING_KEY_ID` | Signing key ID, if required to select a signing subkey |

3. Once the namespace, token and key are ready, create the repository Actions
   variable `MAVEN_CENTRAL_PUBLISH_ENABLED` with value `true`.

## Publish a version

Set `mothlightVersion` in `gradle.properties` to the intended release version
and merge the version change. From **Releases → Draft a new release**, create a
matching `v<version>` tag targeting that commit and publish the GitHub release.
For example, version `0.1.0` uses tag `v0.1.0`. A GitHub prerelease uses a
version with a suffix, for example `0.1.0-rc.1`.

The workflow checks the tag, runs Verify, and publishes signed library artifacts
under `io.github.roman-n1.mothlight`. If publication is disabled, it produces
the unsigned repository artifact instead. Inspect the workflow and Central
Portal deployment before retrying a failed run. Published versions are immutable.
