---
status: proposed
---

# Campaigns persist as versioned JSON files behind an anonymous key

A campaign is saved as a versioned, checksummed JSON file on local disk behind a store interface, keyed by a random key the browser mints and keeps. There are no accounts. The village is frozen while nobody is connected, and an expedition in progress is held (not wiped) when the player disconnects. Full design: `docs/brainstorm/prestige-and-persistence.md`, section 2.

We chose files over H2, SQLite, PostgreSQL and Redis because the game is a single-player campaign on one small instance, the state is one object graph, and the store interface lets PostgreSQL replace it later. We chose an anonymous key over accounts to avoid login, email and password handling for a hobby game.

## Consequences

- A lost key cannot be recovered by the server. Export and import of a campaign file, and a key shown in settings, are the only mitigations.
- The data directory must be a mounted volume. On an ephemeral filesystem every deploy deletes every campaign.
- Nothing produces, eats or starves while the player is away; the tower does not punish absence.
- Exported files carry the tower's seed and mutations sealed (encrypted), so a player cannot read what they have not yet met. The key for that is deployment configuration and must stay stable across deploys.
- Every new piece of village or campaign state must add its snapshot fields, or it is lost on restart.
