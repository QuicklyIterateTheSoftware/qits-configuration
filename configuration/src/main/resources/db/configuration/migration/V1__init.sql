-- The whole schema of qits-configuration, in one migration.
--
-- One V1 and no inherited lineage: this repository starts on PostgreSQL and has never had another
-- store. From here on the ordinary rule holds — keep appending, never edit an applied migration.
--
-- TWO TABLES, AND THE SECOND IS THE AUTHORITY. `configuration_revision` is append-only and every
-- write adds exactly one row to it; `configuration_entry` is the read-optimised HEAD, one row per
-- (application, key) that currently has a value. The current state is reproducible from the log
-- alone, which is what makes an accidental edit answerable rather than merely regrettable.
--
-- NO FOREIGN KEY between them, deliberately. `configuration_entry.head_revision` names a revision
-- seq, but a delete REMOVES the entry row while its revision stays — so the relation only ever runs
-- one way, and an FK would buy nothing that the write path does not already guarantee inside its own
-- transaction. Nor is there any relation to another context: an application here is named by the
-- string qits-platform-deployments knows it by, and that row lives in another physical database.

create table configuration_entry (
    id uuid not null,

    -- The application the entry configures — the deployer's own application name (`qits-gateway`,
    -- `qits-ci`), a dns-label-shaped string. Validated on the way in by ConfigurationKeys; the
    -- column is deliberately not a check constraint, so the vocabulary can be widened without a
    -- migration and every historical row keeps what it was written with.
    application varchar(64) not null,

    -- The EXTRAS GRAMMAR after the application segment: `env.<VAR>`, `mounts[i]`, `publishes[i]`,
    -- `groups[i]` or `aliases[i]`. This service validates the SHAPE of this string and nothing
    -- about the value beside it — qits-platform-deployments' ServiceExtras stays the single parser
    -- of what a mount or a publish means on the platform.
    --
    -- `key` is a non-reserved keyword in PostgreSQL and is a legal unquoted column name. The Java
    -- field is `entryKey`, because KEY *is* reserved in HQL.
    key varchar(256) not null,

    -- The entry's value, verbatim. `text` because no length a schema could pick here would be
    -- anything but a guess — a mount specification is short, an env var may be a URL list.
    value text not null,

    -- What KIND of entry this is. `plain` in v1 and nothing else writes another word; `secret` is
    -- the fold-in of qits-secrets and arrives with the code that can hold one (an in-memory,
    -- approval-gated, one-shot credential is not a value this column may ever carry). No check
    -- constraint, for the reason above.
    class varchar(32) not null,

    -- The revision this value came from — the seq of the row `configuration_revision` gained in the
    -- same transaction. It is what a consumer records to say which configuration it deployed with.
    head_revision bigint not null,

    updated_at timestamp(6) with time zone not null,

    -- Who wrote it: the forward-auth principal, or the machine identity's own name. Nullable
    -- because an entry may be seeded by a bootstrap that has no principal to name, and a fabricated
    -- one would be worse than an honest null.
    updated_by varchar(255),

    primary key (id),

    -- ONE current value per (application, key). It is what makes the upsert an upsert rather than a
    -- race, and what a resolved read leans on to answer without a group-by.
    constraint uq_configuration_entry_application_key unique (application, key)
);

-- The resolved read is `where application = ?` and the listing groups by it; the unique constraint
-- above already indexes (application, key) with application leading, so no second index is created
-- here. A single-column index on `application` would be that constraint's prefix and nothing more.

create table configuration_revision (
    -- IDENTITY, not a uuid: a revision is an ORDER as much as it is a row. `newest first` and
    -- `which revision did the deployer read` are both statements about this number, and a random
    -- id could express neither.
    seq bigint generated always as identity,

    application varchar(64) not null,
    key varchar(256) not null,

    -- NULL when `deleted` is true, and non-null otherwise. Two columns rather than one because a
    -- deletion is not the empty string: an entry may legitimately hold `` as its value, and a
    -- sentinel would make the two indistinguishable the first time somebody wanted an empty
    -- QITS_ variable.
    value text,
    deleted boolean not null,

    updated_by varchar(255),
    updated_at timestamp(6) with time zone not null,

    primary key (seq)
);

-- The history route reads `where application = ? order by seq desc`, and the write path reads the
-- application's own head as `max(seq) where application = ?`. Both are this index.
create index idx_configuration_revision_application_seq
    on configuration_revision (application, seq);
