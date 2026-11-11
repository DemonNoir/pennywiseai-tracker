# Automated upstream updates

`Upstream Sync` prepares an upstream update for this custom fork. It runs weekly
on Sunday at 01:17 UTC (08:17 Asia/Bangkok), and can be started from **Actions → Upstream
Sync → Run workflow**. The workflow is restricted to
`DemonNoir/pennywiseai-tracker` and uses
`sarim2000/pennywiseai-tracker`'s `main` as upstream. Scheduled runs use the
fork's `master` as their base and publish candidates. Manual runs accept
`base-ref` (default `master`) and `dry-run` (default `true`). Keep `dry-run`
enabled to prepare a merge and report without publishing a branch or PR.
Candidate build/tests run only after publishing, so a successful dry run does
not establish that the candidate builds.

## Immediate upstream notifications

The workflow also accepts the authenticated `repository_dispatch` event
`upstream_updated`. This receiver is ready, but an upstream sender is not
installed. A push to the upstream repository does not automatically dispatch
an event to this repository. Configuring a webhook on upstream requires its
owner/admin's cooperation; the connected account currently has read access
there. See [GitHub webhook permissions](https://docs.github.com/en/webhooks/types-of-webhooks).

If the upstream owner connects a webhook relay or an upstream workflow, its
authenticated sender can call `POST /repos/DemonNoir/pennywiseai-tracker/dispatches`
with `{"event_type":"upstream_updated"}`. The sender needs credentials that
permit repository dispatches to this custom repo. The receiver always uses our
configured `master` and upstream `main`; it does not take refs, repository URLs,
or shell commands from the event payload. See [GitHub repository dispatch](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#repository_dispatch).

Until that sender exists, the weekly schedule performs the checks. A run with
no new upstream commits exits before building APKs. Manual runs remain
available at any time.

## What happens

1. Fetch both histories and attempt a normal Git merge on a temporary candidate.
   Git is allowed to report conflicts; the bot does not choose one side of a
   conflict or discard custom changes.
2. If upstream is already included, finish without an update PR. If the merge
   conflicts, abort it, fail the run, and upload a diagnostic report. The base
   branch stays at its previous commit.
3. For a publishing run, first disable any enabled auto-merge and restore draft
   status on an existing candidate PR before changing its head. Publish a clean
   merge to the bot-owned `auto/upstream-sync` branch, then create or update its
   draft PR against the selected base branch.
4. Call the reusable `Tests` workflow on the candidate's immutable commit SHA.
   This runs parser tests, custom integration checks, app unit tests, and debug
   APK assembly. A failed gate leaves the update for investigation.

The bot does not merge the PR or publish a release. Passing CI establishes the
listed checks for that commit; it is not a claim that device behavior has been
verified. Read the run summary and the `upstream-sync-report` artifact for the
candidate and failure details. Debug APKs and test reports are available from
the corresponding test jobs. These APKs use debug signing; they are not signed
release replacements for an installed app with another certificate.

If upstream changes `.github/workflows/`, publication stops before changing any
bot branch or PR. Those definitions need separate review and integration: the
built-in token cannot publish workflow changes. Dry runs can still report merge
feasibility. See the [upstream-sync action author's token limitation](https://github.com/peter-evans/create-pull-request/blob/main/docs/examples.md#keep-a-fork-up-to-date-with-its-upstream).

Treat `auto/upstream-sync` as disposable automation output. The next run can
refresh it with a force-with-lease push. Put manual conflict fixes or other
development on another branch, and rerun checks after any changes or when the
base branch advances.

The privileged preparation job checks out the workflow caller's commit into a
separate `automation` directory, copies its sync script outside the candidate,
and runs that trusted copy. Candidate files are not executed in the publishing
job. Test jobs execute candidate Gradle code with a read-only token and no
inherited secrets. Merge commit timestamps are taken from the upstream commit,
and the bot identity/message are fixed, so the same base and upstream commits
produce the same merge commit ID. This avoids generating a new candidate ID
solely because a scheduled run happens later.

## Enable on GitHub

1. Publish the custom source commits and these workflow files to the fork's
   default branch, `master`. Scheduled runs use the workflow on that branch.
2. Enable GitHub Actions in the fork's **Actions** tab if GitHub has disabled it.
   Enable `Upstream Sync` itself if its schedule is disabled.
3. In **Settings → Actions → General**, allow the actions used by the workflows.
   Under **Workflow permissions**, enable **Allow GitHub Actions to create and
   approve pull requests**. The bot creates PRs; it does not approve them.
   Repository or organization policy must permit the declared `contents: write`
   and `pull-requests: write` permissions for the publishing job. Build jobs use
   read-only access. No personal access token is required for upstream sync.
4. Run **Tests** manually on the intended source baseline first. Then run
   **Upstream Sync** manually with `dry-run` enabled and inspect the summary
   and report. When ready to publish a candidate, rerun with `dry-run` disabled;
   its direct `Tests` call will produce the candidate APKs and test reports.
   Inspect that hosted result before relying on the schedule.
5. Enable GitHub Actions failure notifications for the account following this
   repository if failure alerts are wanted. The workflow reports failures in
   GitHub; it does not configure account email or send chat messages.

GitHub may delay scheduled runs. Public fork schedules start disabled, and
public repositories without activity for 60 days can have schedules disabled
again. See [GitHub's workflow activation documentation](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/disable-and-enable-workflows)
and [schedule and manual trigger rules](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows).

The sync workflow directly calls `Tests` rather than depending on the PR event.
GitHub's current `GITHUB_TOKEN` rules can put runs for bot-created PRs into an
approval-required state; direct reusable invocation lets this sync run execute
its validation without depending on that approval. See
[GitHub token event rules](https://docs.github.com/en/actions/concepts/security/github_token)
and [repository PR creation settings](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository).

## Custom feature coverage and remaining checks

The existing `scripts/check-custom-integration.sh` checks that selected source
entry points for slip discovery, SMS-triggered scans, merchant learning,
rescan, and reset remain connected. App tests also cover slip parsing,
merchant learning, backups, currency rules, and OCR model file validity.
The ONNX models, Thai and English Tesseract data, recognition dictionary, and
Kanit fonts are tracked in Git and can be obtained by a clean checkout.
Native OCR dependencies are resolved through Gradle.

These checks do not run a photo through the native OCR engines on an Android
device. `SlipRealOcrIntegrationTest` parses embedded OCR text; the local batch
OCR report test is ignored and depends on local files. CI success does not
exercise photo permission dialogs, the media observer, background restrictions,
or the rendered UI.

Before shipping an update, follow
[the custom integration device checks](custom-upstream-integration.md): scan a
slip, start scans from Home and onboarding, test merchant suggestions and
rescan, and test the learned-name reset dialog. If database migrations changed,
also test the old-version upgrade path on `Api26_Migration`, as required by
[the migration guide](database-migrations.md). Check old backups and existing
slip data as part of that upgrade path. CI does not provide an API 26 emulator
or an automatic rollback of installed-app data.

## Local APK versus GitHub APK

GitHub builds committed source on GitHub. Files that exist only in a developer's
working tree, ignored files, local Gradle properties, and local signing keys
are not included. A successful local APK is only a comparable baseline after
its source changes have been committed and published.

During initial setup, the local checkout had custom commits ahead of
`origin/master`. The review branch also preserves the local billing gateway
relocation, standard-flavor billing bindings, and release signing fallback in
commit `712706e6`. The fork's default branch must receive the intended source
baseline before scheduled sync can use it. IDE settings and local scratch files
are excluded. CI builds debug APKs, so even identical app source does not imply
the same release binary or signing identity as a locally built APK.

## Local verification of automation changes

Validate the workflow syntax and shell script, run the sync script in a
disposable clean checkout, and run `./init.sh` as required by the agent guide.
The script only prepares a candidate locally; the workflow controls publishing
and tests. Set `SYNC_REPORT_DIR` to a directory outside the checkout to retain
the report, and invoke the trusted script from its absolute path while the
current working directory is the disposable checkout. A dirty checkout is
rejected. `SYNC_UPSTREAM_URL` and `SYNC_UPSTREAM_BRANCH` can point to local
fixture repositories for conflict/no-update tests.

Local checks cannot establish GitHub permissions, hosted-runner availability,
schedule activation, or successful artifact upload. The first hosted baseline
passed in [run 42495200828](https://github.com/DemonNoir/pennywiseai-tracker/actions/runs/42495200828).

## Standard debug and release APKs

CI also builds `standardRelease` separately with R8 and resource shrinking.
`standard-debug-build` and `standard-release-build` are intermediate artifacts
with temporary CI signatures. The release intermediate contains the universal
APK; the variants are built in separate Gradle invocations so F-Droid task
selection does not affect standard release signing or ABI splits.

After all checks pass, an isolated job signs both standard variants using the
optional `PERSONAL_APK_KEYSTORE_BASE64` repository secret. This is the existing
personal Android debug keystore (alias/password `androiddebugkey`/`android`),
whose public certificate fingerprint is checked in the workflow. It is suitable
for this personal build, and is not a production Play Store signing identity.
The signing job runs no repository code and receives APK artifacts only. PR
runs never receive the key. The reusable upstream workflow passes only this
secret to the signing job; Gradle build/test jobs do not reference it.

Final download names are `standard-debug-apk` and `standard-release-apk`.
Without the secret, CI reports that stable signing is unavailable and leaves
only intermediate builds. Do not treat those as updates for the installed app.
Release uses `com.pennywiseai.tracker`; debug uses
`com.pennywiseai.tracker.debug`, so they can be installed alongside each other.
Updating an existing installation also requires a compatible version code;
Android will reject a downgrade. Creating an artifact does not publish a
GitHub release or change the application's version.
