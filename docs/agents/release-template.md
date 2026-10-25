# Releases

A release is a tag, a closed milestone and a GitHub release whose notes follow the template below. `.github/release.yml` configures GitHub's generated notes, which group merged pull requests by label (`enhancement`, `bug`, `documentation`). Commits go straight to `main`, so generated notes list nothing today; write the notes from the milestone's issues instead, and use `--generate-notes` only for work that arrives as pull requests.

## Conventions

- **Tag**: `MAJOR.MINOR.PATCH`, unprefixed (`2.2.0`, not `v2.2.0`), lightweight, on the last commit of the release on `main`. Push it with `git push origin <tag>` only when the release is meant to be public.
- **Milestone**: named after the tag it ships in (`2.2.0`). A milestone with open issues is not a release yet: move the unfinished issues to the next milestone or finish them first.
- **Version numbers**: `backend/pom.xml` and `frontend/package.json` carry the version and should equal the tag. Both are bumped in the commit the release is tagged on (they read `2.0.0` through 2.2.0, then the tag's own version from 3.1.0).
- **History**: never rewrite `main` after a tag is pushed. A tag is moved only on the owner's explicit request.

## Before tagging

- [ ] Every issue in the milestone is closed, or moved out with a comment saying why.
- [ ] `mvn test` from `backend/` passes, and `ng build` and `ng test` from `frontend/` pass.
- [ ] Any change to what a saved game contains has a schema bump and a migration (`docs/adr/` and the persistence notes in `docs/brainstorm/prestige-and-persistence.md`), and the golden fixture is current.
- [ ] Persisted ids introduced since the last release are appended to `backend/src/main/resources/persistence/released-ids.txt`. The freeze test fails the build if a released id is later removed or renamed without a migration.
- [ ] The version in `pom.xml` and `package.json` equals the tag.
- [ ] Production-only settings are listed under "Running it" below if they changed (`game.persistence.seal-key`, `game.seed-secret`, trial dates under `game.trial.races`).
- [ ] `CONTEXT.md` terms the release ships are no longer marked Planned.

## Cutting it

```sh
git fetch origin && git status -sb          # main is clean and equals origin/main
git tag <version> origin/main               # lightweight tag
git push origin <version>
gh release create <version> --title "<version>: <name>" --notes-file release-notes.md
```

Add `--prerelease` for a release that is not meant to be played yet. Close the milestone afterwards: `gh api --method PATCH repos/diogohmcruz/TowerDungeon/milestones/<number> -f state=closed`.

Gather the issues for the notes with:

```sh
gh api "repos/diogohmcruz/TowerDungeon/issues?milestone=<number>&state=closed&per_page=100" \
  --jq '.[] | select(.pull_request | not) | "- #\(.number) \(.title)"'
```

## Notes template

Copy this into `release-notes.md`, replace every `<...>`, and delete sections that have nothing to say. Write in the voice of the game (serious, plain, no cheerfulness) and use the terms in `CONTEXT.md`.

```markdown
## <version>: <a short name for the release>

<Two or three sentences on what a player will notice first. No marketing words.>

### What is new

- <Player-facing change> (#<issue>)
- <Player-facing change> (#<issue>)

### Fixed

- <What was wrong, in one line> (#<issue>)

### Saved games

<One of: "No change to saved games." or "Saves from <older version> load and are migrated; a copy is kept as `pre-migration-v<n>.tdc`." Name any schema bump and anything that can no longer be read.>

### Running it

- <New or changed setting, its default and whether production needs it. For example: `game.seed-secret` must be set in production.>
- <New endpoint, profile or port, if any.>

### Known issues

- <Open issue in this milestone or a regression we know about> (#<issue>)

### Not in this release

- <Planned work people may expect, and where it is tracked> (#<issue>)

**Full changelog**: https://github.com/diogohmcruz/TowerDungeon/compare/<previous tag>...<version>
```

## Notes for the writer

- List changes by what the player or the operator sees, not by file. One line per issue; join issues that make one change.
- Do not copy commit trailers or agent attribution into the notes.
- Link issues with `#<number>`; GitHub turns them into links in the release page.
- If a number in the game is a placeholder pending the scaling pass (#8), say so under "Known issues" instead of presenting it as tuned.
