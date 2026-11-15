# Releasing Kino

Kino is **not released by merging pull requests**. The [Release APK workflow](.github/workflows/release.yml) runs only when a new `vMAJOR.MINOR.PATCH` tag is pushed.

## Checklist

1. Merge all intended changes into `master`. Wait until **Android CI** passes.
2. Set both `versionName` and an increased `versionCode` in `app/build.gradle.kts`, and merge the change into `master`.
3. Make sure release signing and integration secrets exist in GitHub Actions. The release build requires `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `TMDB_API_KEY` and `OPENSUBTITLES_API_KEY`; releases including bundled SKTorrent credentials also require `SKTORRENT_USERNAME` and `SKTORRENT_PASSWORD`.
4. Tag the **merged** commit, matching `versionName` exactly:

   ```bash
   git switch master
   git pull --ff-only
   git tag -a v0.1.3 -m "Kino 0.1.3"
   git push origin v0.1.3
   ```

5. Inspect the **Release APK** GitHub Actions run and the GitHub release `v0.1.3`. Once published, the in-app updater detects the greater `versionCode`.

## Safeguards

- Tag names must follow `vMAJOR.MINOR.PATCH` and match the `versionName` in the tagged commit.
- A tag pointing to an unmerged branch is rejected.
- The job runs tests, lint, signed APK build, signature check, and validates both TorrServer ABIs.
- Existing GitHub release assets are **never overwritten**. Do not move published tags.
- A failed release build can be inspected in GitHub Actions; investigate the failure before creating a new tag.
- Editing Gradle configuration or merging PRs alone cannot publish a release.

The `0.1.3` example assumes both PR #31 (TorrServer MatriX.150) and PR #30 (SKTorrent credentials, version bump) have been merged.
