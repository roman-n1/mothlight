# Maven Central releases

GitHub Actions builds and signs the libraries. To check packaging from a browser,
open **Actions → Publish → Run workflow**. The run executes Verify and the published
consumer check, then prepares an unsigned Maven repository and reports the estimated
Central file count, size before ZIP compression, and largest files in the Actions
summary. It works without Central credentials.

The release uses `-PmothlightReleaseCoreOnly=true` for preparation, the consumer
check and upload. This publishes eight library modules with all their platforms.
The optional `mothlight-room3` adapter stays available from source and is included
in regular pull request checks.

Before enabling publication or retrying a release, check the remaining monthly
allowance in [Central Portal Usage Center](https://central.sonatype.com/publishing/usage?org=roman-n1).
Use the organization totals if the namespace shares its quota with other projects
such as Actron. Confirm there is also room for a release, then set the repository
Actions variables `MAVEN_CENTRAL_REMAINING_FILES` and
`MAVEN_CENTRAL_REMAINING_BYTES` to the **remaining** file and size allowance as
whole numbers. Refresh both values before every release and after any other
publication that shares this quota. The workflow cannot read live usage.

The release upload requires both budgets and stops if either estimate exceeds
the configured allowance. The estimate includes one signature and two checksums
per payload, with a size reserve for each signature. Manual runs report usage
even when neither budget is configured.

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
For Beta 1, publish a GitHub prerelease tagged `v1.0.0-beta.1`.

The workflow checks the tag, runs Verify, and publishes signed library artifacts
under `io.github.roman-n1.mothlight`. If publication is disabled, it produces
the unsigned repository artifact instead. Inspect the workflow and Central
Portal deployment before retrying a failed run. Published versions are immutable.
