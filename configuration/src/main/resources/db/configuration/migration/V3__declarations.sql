-- DECLARATIONS: what an application says its own configuration keys ARE.
--
-- THE DOCTRINE AMENDMENT, written down here because this is the migration that makes it structural.
-- V1 and V2 are the store of a service that STORES AND DOES NOT PARSE: an entry's value is bytes it
-- keeps, versions and hands back, and what a mount or a published port MEANS is
-- qits-platform-deployments' ServiceExtras and nobody else's. That line is untouched — nothing below
-- reads an entry value, and nothing ever will.
--
-- What arrives with these three tables is a SECOND DOCUMENT CLASS. `.config/qits/configuration.yml`
-- is the application's own declaration of its keys: which ones exist, what type each is, what it
-- falls back to. This service parses THAT, and it has to, because the questions a declaration
-- answers cannot be answered anywhere else — a serviceAddress renders differently in every
-- environment and differently again depending on which plane the named service deploys onto, and no
-- application knows either fact about itself. Whoever serves the typed read is the one place both
-- facts meet. One document class, one parser (control/DeclarationParser), and the entry values stay
-- exactly as unread as they were.
--
-- THREE TABLES AND THE THIRD IS THE AUTHORITY, the same shape V1 chose: the declaration and its keys
-- are the read-optimised current state, `configuration_declaration_revision` is append-only and
-- records every intake and every removal. A declaration is deletable — the entry rows are not — and
-- that is exactly why the log matters more here, not less: the delete door exists so a bad tag can
-- be re-cut, and a re-cut tag with no record of the first one would be a version that quietly meant
-- two things.
--
-- NO FOREIGN KEYS, between these tables or to configuration_entry. The same reasoning V1 gives, plus
-- one of its own: a declared key names an entry key that may not exist yet and may never exist, and
-- an entry may outlive every declaration that ever mentioned it. Those are not broken references,
-- they are the two halves this service exists to hold side by side and report the difference of.
--
-- NO deployment_target IN THE DOCUMENT, and a not-null column for it here. Which plane an
-- application deploys onto is the deployer's fact — `deployment_target` in its own
-- .config/qits/deployments.yml — so the pipeline that posts the declaration passes it with the seed
-- and this table records what it was told. A file asserting its own plane would be a second answer
-- to a question qits-platform-deployments already answers, and the day a service is promoted the two
-- would disagree with a container dialling the void as the outcome.

create table configuration_declaration (
    id uuid not null,

    -- The application the document belongs to — the deployer's own application name, carrying the
    -- qits- prefix (`qits-events`), a dns-label-shaped string validated on the way in. Plain varchar
    -- with no FK, like every other `application` in this schema: that row lives in another physical
    -- database and a declaration outlives the catalogue entry that described it.
    application varchar(64) not null,

    -- The version the document was published under: the release version or tag the pipeline built.
    -- Looser than a dns label on purpose — this service does not get to refuse a spelling qits-ci
    -- already shipped — but still a single path segment. See ConfigurationKeys.
    version varchar(128) not null,

    -- `platform` or `environment`. NOT NULL and no default: it decides whether a serviceAddress
    -- pointing at this application renders as `qits-events` or as `dev-qits-events`, and a default
    -- would be a wire alias nobody chose. No check constraint, for the reason V1 gives about `class`
    -- — the vocabulary can widen without a migration and every historical row keeps its word.
    deployment_target varchar(32) not null,

    -- SHA-256 of the raw document, hex. It is what makes a re-post either a no-op or a conflict:
    -- same bytes is the idempotency every pipeline that retries a step depends on, different bytes
    -- under the same version is two builds disagreeing about what that release declared.
    content_hash varchar(64) not null,

    -- The document verbatim, comments and descriptions included. `text` because no length here could
    -- be anything but a guess, and because this column is the only thing that can settle an argument
    -- about what a version actually declared.
    raw text not null,

    received_at timestamp(6) with time zone not null,

    -- The machine identity that posted it, or null when there was no name to record. Nullable for
    -- the same reason updated_by is: a fabricated principal is worse than an honest absence.
    received_by varchar(255),

    primary key (id),

    -- ONE DOCUMENT PER (application, version). A version is a fixed point; this constraint is what
    -- makes the second post a decision (no-op or conflict) instead of a race.
    constraint uq_configuration_declaration_application_version unique (application, version)
);

create table configuration_declared_key (
    id uuid not null,

    application varchar(64) not null,
    version varchar(128) not null,

    -- The declared key, in the SAME extras grammar a stored entry key uses and validated by the same
    -- ConfigurationKeys.requireKey. One grammar: a key this service would refuse at a PUT must not be
    -- declarable either, or a declaration could promise a default for a key nobody can ever set.
    -- `key` is unquoted-legal in PostgreSQL and reserved in HQL, so the Java field is `declaredKey`.
    key varchar(256) not null,

    -- One of string, boolean, number, serviceAddress, packageVersion. `type` is likewise legal here
    -- and a function name in HQL, so the Java field is `declaredType`.
    type varchar(32) not null,

    -- The declared fallback, as text, for string/boolean/number. Null means the key was declared
    -- with no default, which is a different statement from a default of '' — the same distinction
    -- configuration_revision keeps between a deletion and an empty value. serviceAddress and
    -- packageVersion never carry one: the first is rendered by the platform, the second is put there
    -- by whatever released it, and a fallback for either is a container started on an address or a
    -- tag nobody shipped.
    default_value text,

    -- serviceAddress only: the application addressed, and the port on it. The HOSTNAME is not stored
    -- — it is derived per read from this name and the target's own deployment_target, because it
    -- differs per environment. Storing it would be storing one environment's answer in a
    -- platform-plane table.
    service_ref varchar(64),
    service_port integer,

    -- packageVersion only: which package the version under this key is a version of.
    package_type varchar(32),
    package_name varchar(255),

    primary key (id),

    constraint uq_configuration_declared_key unique (application, version, key)
);

-- THE REVERSE QUESTION, and the reason this index is here before anything asks it: "which
-- applications carry a version of qits/workspace, and under which key". That is the question
-- qits-artifacts' collector asks when it decides which images it may delete, and it is the one this
-- table can answer for every application at once where the hand-maintained ImagePins list answers it
-- for four. The index is what keeps that a lookup rather than a scan of every key ever declared.
create index idx_configuration_declared_key_package
    on configuration_declared_key (package_type, package_name);

create table configuration_declaration_revision (
    -- IDENTITY, like configuration_revision.seq and for the same reason: an intake is an ORDER as
    -- much as a row. "Which declaration governs this application right now" is answered by walking
    -- this column downwards, and a random id could not express it.
    seq bigint generated always as identity,

    application varchar(64) not null,
    version varchar(128) not null,

    -- NULL exactly when `deleted`. Two columns rather than a sentinel, the same pair
    -- configuration_revision uses: a removal is not a document with an empty hash.
    content_hash varchar(64),
    deleted boolean not null,

    received_by varchar(255),
    received_at timestamp(6) with time zone not null,

    primary key (seq)
);

-- The governing read is `where application = ? order by seq desc` and stops at the first row that is
-- not a deletion and still names a live declaration. That is this index.
create index idx_configuration_declaration_revision_application_seq
    on configuration_declaration_revision (application, seq);
