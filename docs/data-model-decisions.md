# Data Model Decisions

Record of the single-source-of-truth decisions taken during the Data Consistency & Modelling
epic (P1). Keep this file in sync whenever the data model changes.

## User ↔ Playlists

- **Source of truth:** the `Playlists` table, queried via the `ownerId-index` GSI.
- The `Users` table no longer embeds a `playlists` collection. The `User` aggregate does not hold
  playlists (the collection was removed from the domain model and the DynamoDB document).
- `MAX_PLAYLISTS_PER_USER = 10` is enforced by `CreatePlaylistService` against a persistent count
  (`PlaylistRepository.countByOwnerId`) before persisting.
- Concurrency note: the limit uses a count-then-insert pattern. For a user, two truly concurrent
  create requests could in principle both observe 9 and insert — a residual race we accept for P1.
  A strictly serializable counter (conditional increment on the User item) is the follow-up if the
  race becomes a concern.

## Album ↔ Songs

- **Source of truth:** the `Songs` table, each row carrying its `albumId`. Songs are queried via
  the `albumId-index` GSI on `Songs`.
- The `Albums` table and the `Album` aggregate no longer embed a `songs` collection, so there is no
  divergent list to keep in sync when a song is uploaded.

## Playlist updates (concurrency)

- Playlists carry a `version` attribute. Mutations (`addSong`, `removeSong`, `updateDetails`) are
  persisted with a conditional write `version = :expected` and the stored version is bumped.
- A write with a stale version fails with `PlaylistConcurrentModificationException` instead of
  silently overwriting another client's change.

## User registration (email uniqueness)

- **Source of truth:** the `UserEmails` table, keyed by the (normalized) email address.
- Registration writes the user and an email marker in a single `TransactWriteItems`; the email
  marker uses `attribute_not_exists(email)` so a concurrent second registration with the same
  email is rejected atomically.

## Song upload (S3 + DynamoDB partial failure)

- Order: generate S3 upload URL → persist song metadata.
- If metadata persistence fails after a multipart upload was created, the adapter attempts
  `abortUpload` to remove the orphan multipart upload and logs the outcome. Single-part presigned
  URLs do not create an object until the client PUTs it, so there is nothing to clean up.
- `@Transactional` was removed from the song upload services: it provided a false sense of
  atomicity across S3 and DynamoDB, which are separate systems.