-- ENV BECOMES PART OF THE IDENTITY OF AN ENTRY.
--
-- V1 was written for an ENVIRONMENT service: one instance per tier, every row in it implicitly that
-- tier's, and the env named nowhere because there was nothing to distinguish. This service is being
-- promoted to the PLATFORM plane, where one instance holds every environment's configuration side by
-- side — so the thing that was implicit in the deployment has to become explicit in the row. An
-- entry is `(env, application, key)` from here on, and so is the unique constraint that makes an
-- upsert an upsert rather than a race.
--
-- WHY THAT IS AN IMPROVEMENT AND NOT A CONCESSION. Two tiers' configuration in one store is what
-- makes "what does prod set that dev does not" a query instead of two browser tabs, and what lets an
-- environment that is joining the platform start from what its siblings already say rather than from
-- an empty table. The old worry — an edit in dev is an edit in prod — is answered by the key, not by
-- the deployment: a write names the env it writes, and there is no route that reaches a row without
-- one.
--
-- THE BACKFILL VALUE ARRIVES AS A DEPLOY-TIME PLACEHOLDER, and it has to. Every row already here was
-- written by an instance that WAS an environment, so they all belong to exactly one env — but a
-- platform service is handed no tier name in-band: it is not deployed per tier any more, so nothing
-- in the process's own environment says which tier the rows it inherited came from. The operator
-- performing the cutover knows, and says so once, as `QITS_CONFIGURATION_LEGACY_ENV`. The property
-- has no default on purpose (see META-INF/microprofile-config.properties): an unset value fails
-- migrate-at-start naming the missing expression, which is the only honest outcome — a guess here
-- would stamp every historical row with an env nobody chose and the log would say it was deliberate.
--
-- The column is added nullable, backfilled, and only then made NOT NULL. Doing it in one step would
-- need a default, and a default on a column whose whole point is that it must be stated is a way for
-- a later insert to quietly pick one.

alter table configuration_entry add column env varchar(64);
update configuration_entry set env = '${legacy_env}';
alter table configuration_entry alter column env set not null;

alter table configuration_revision add column env varchar(64);
update configuration_revision set env = '${legacy_env}';
alter table configuration_revision alter column env set not null;

-- ONE CURRENT VALUE PER (env, application, key). The V1 constraint said (application, key), which on
-- a platform instance would let dev's value and prod's value fight over one row. Dropped rather than
-- kept beside the new one: leaving it would forbid exactly the thing this migration exists to allow.
alter table configuration_entry
    drop constraint uq_configuration_entry_application_key;
alter table configuration_entry
    add constraint uq_configuration_entry_env_application_key unique (env, application, key);

-- The history route reads `where env = ? and application = ? order by seq desc`, and the write path
-- reads the head as `max(seq) where env = ? and application = ?`. Both are this index, and neither is
-- served by the V1 one any more — env leads because every read now carries it.
drop index idx_configuration_revision_application_seq;
create index idx_configuration_revision_env_application_seq
    on configuration_revision (env, application, seq);
