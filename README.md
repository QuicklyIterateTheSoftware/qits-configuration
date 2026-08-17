# qits-configuration

Deployment configuration as platform state: the entries an environment's applications are deployed
with, stored, versioned and served.

## What it replaces

qits-platform-deployments reads a deployment's extra environment, mounts, published ports, groups
and network aliases from `qits.platform.deployments.extras.<app>.*`. Those keys lived in a
hand-edited properties file on the deployer's own config volume, snapshotted at deployer boot — so
an edit was inert until the deployer was forced to reload, and a live fix applied to a running
service was reverted by the next deploy.

This service owns the entries instead. Every write is versioned and attributed; the deployer pulls
the resolved answer per deployment and records the revision it deployed with. Nothing pushes
configuration into a deployment: the deployer *reads*, with its own machine identity.

## The model

Two tables, and the second is the authority.

    configuration_revision   append-only. Every write that changes something adds exactly one row:
                             (application, key, value, deleted, seq, updatedBy, updatedAt).
    configuration_entry      the read-optimised HEAD. One row per (application, key) that currently
                             has a value, naming the revision it came from.

The current state is reproducible from the log alone, which is what makes an accidental edit
answerable rather than merely regrettable. A delete appends a revision and removes the head row, so
the value that was removed is still readable.

**An identical write appends nothing.** That is what makes a bootstrap free to re-import its file on
every boot, and it keeps the history a record of changes rather than of runs.

### The key grammar

A key is the extras grammar *after* the application segment:

    env.<VAR>       VAR matches [A-Za-z_][A-Za-z0-9_]*
    mounts[i]       i is one to four digits
    publishes[i]
    groups[i]
    aliases[i]

An application name is dns-label shaped. Anything else is a 400 that names what is wrong.

**This service parses no values.** What a mount, a published port or an alias *means* is read by
qits-platform-deployments' own `ServiceExtras`, which stays the single parser on the platform. The
key's shape is checked here because the deployer refuses a deployment carrying a key it does not
recognise — checking at the write turns that into a 400 the person who typed it reads, instead of a
failed deployment hours later.

## The API

Everything under `/configuration/api`. Every route accepts `qits:admin` (a person, through
qits-gateway's forward-auth headers) or `qits:system` (a machine, through a bearer validated against
qits-platform-idp). There is no anonymous route.

| route | what it answers |
| --- | --- |
| `GET /applications` | every configured application, with its entry count and head revision |
| `GET /applications/{app}/resolved` | **the deployer's read** — `{headRevision, properties}`, the properties at their full `qits.platform.deployments.extras.<app>.<key>` names |
| `GET /applications/{app}/entries` | the current entries |
| `PUT /applications/{app}/entries/{key}` | set one value. 201 the first time, 200 after; an identical value writes no revision |
| `DELETE /applications/{app}/entries/{key}` | remove one entry, keeping it in the history |
| `GET /applications/{app}/history` | every revision, newest first |
| `POST /import` | `text/plain`, an extras properties file whole. Idempotent; answers `{imported, unchanged, ignored}` |

The resolved read carries **complete property names** on purpose: a consumer layers the map as a
configuration source verbatim, with no prefix to re-assemble and no second place for the deployer's
namespace to be written down. That namespace has moved twice already.

The framework's own paths sit under `/configuration/q` — `/configuration/q/health/ready` is what the
deployer's health gate curls, and `/configuration/q/openapi` is the document.

## Running the tests

    ./mvnw clean verify

No docker, no network beyond Maven Central and the platform's own Maven repository, no credentials.
The suite spawns a real PostgreSQL of its own — zonky's binaries, resolved as ordinary Maven
artifacts and started as a child process.

To probe the packaged artifact as well:

    ./mvnw clean verify -DskipITs=false     # the fast-jar
    sdk env && ./mvnw clean verify -Dnative # the GraalVM binary

## The modules

    configuration/  the domain — entity, persistence, control, dto, mapper, error. No JAX-RS. Owns
                    the datasource, the persistence unit and the Flyway lineage.
    service/        the adapters — the JAX-RS routes, the exception mapper, and the native-image
                    reflection registration for what Jackson binds.

There is **no client** in v1. A browsing and editing UI is a later phase.
