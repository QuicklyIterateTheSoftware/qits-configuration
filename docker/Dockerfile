# qits-configuration as a GraalVM-native container IMAGE — the same shape as every other qits
# service's docker/Dockerfile: a Mandrel builder stage compiles the binary, a minimal runtime stage
# carries only it.
#
# This file is not how the binary is built; it is how the binary is SHIPPED. `.sdkmanrc` names
# 25.0.2-graalce, so `sdk env && ./mvnw package -Dnative` produces service/target/qits-configuration
# locally with no container involved. What this adds is the image around it — and a way for a machine
# with no GraalVM to get a binary at all, since the builder stage brings its own native-image.
#
# Build from the repo root:
#
#     docker build -t qits/configuration:latest -f docker/Dockerfile .
#
# ONE LINE, because there is no client. The SPA-serving siblings need a submodule checkout and an
# npm build before this command, since their bundle cannot be built inside a docker build; this
# service serves machines and an operator, so there is nothing to prepare. When a client arrives,
# the Quinoa flags and the `test -f` guard arrive with it — copy them from qits-events.
#
# --- `docker run` with no configuration FAILS, and that is the honest behaviour -------------------
# The `configuration` jar's datasource is an EXPRESSION over the platform's generic resource contract
# — `jdbc.url=${QITS_RESOURCE_DB_URL}` and its username/password siblings — with no fallback url at
# all. Unset, the expressions are unresolvable and the process dies during Flyway's first connect
# naming the missing variable.
#
# That is a missing-configuration failure, not a packaging one, and it is deliberately NOT patched
# here with an `ENV`: no address this image could name would be right on a real platform, and a
# default would only make the bare run "work" against a store nobody meant. It matters more here than
# in most services — this database is where a deployment's environment is read from — so opening the
# wrong one silently would be the worst failure this image could have. What a deployment supplies
# instead is the triple:
#
#     QITS_RESOURCE_DB_URL=jdbc:postgresql://<tier>-qits-oci-postgresql:5432/qits_configuration
#     QITS_RESOURCE_DB_USERNAME=qits_configuration
#     QITS_RESOURCE_DB_PASSWORD=…
#
# and nobody types those: `.config/qits/deployments.yml` declares `resources: postgresql:db`, and
# qits-platform-deployments creates the role and the database before this container starts and
# injects all three. This image carries no volume for data — the store is another container's.
#
# --- stage `build`: native-compile ----------------------------------------------------------------
# The builder JDK MUST be 25: native-image cannot read bytecode newer than its own JDK, and this
# project targets release 25.
#
# `mirror.dev.localhost:8080/quay/…` is qits-platform-mirror's pull-through mirror of quay.io: the
# first build fetches the image from upstream and caches it, every later build is served from local
# disk. The tag is upstream's, unchanged — the mirror revalidates it, so `jdk-25` still moves when
# quay moves it.
FROM mirror.dev.localhost:8080/quay/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 AS build
USER root
WORKDIR /src

# Fetch the Maven distribution first, keyed only on the wrapper files, so a source-only change does
# not re-download it.
COPY mvnw .
COPY .mvn/ .mvn/
RUN ./mvnw --version

# The whole reactor, not a module list. `-am` builds service's dependency from source —
# `qits-configuration-domain` is a version this repo never publishes, so a builder container with an
# empty ~/.m2 has nowhere else to get it. Copying module by module would mean editing this file every
# time one is added, and forgetting to would fail the build with an unresolved dependency rather than
# anything that names the real cause. `.dockerignore` is what keeps this cheap.
COPY . .

# --- where the three qits jars come from ----------------------------------------------------------
# This repo depends on qits-db-core (PatientPgDriver, DbRetry), qits-auth-core (the forward-auth
# mechanism and the machine gate) and qits-arch-rules, which live only on the platform's own Maven
# repository. That one IS reachable from a build: the CI recipe passes the address the step container
# can use, and `.qits-maven-settings.xml` mirrors the `qits-maven` repository id onto it — an exact
# id match, which is what gets past Maven's external:http:* blocker without permitting arbitrary HTTP
# repositories. The ARG default is the developer-host address, so a plain host-side `docker build`
# still works.
ARG QITS_MAVEN_REPOSITORY_URL=http://registry.dev.localhost:8080/artifacts/maven/maven
ENV QITS_MAVEN_REPOSITORY_URL=$QITS_MAVEN_REPOSITORY_URL

# --- and how Maven authenticates, once that repository stops answering anonymously -----------------
# The two secret mounts put the CI run's commissioned client into THIS `RUN`'s environment and
# nowhere else — not a layer, not the image history. That is why they are not build args: this image
# is pullable, and a build arg is recorded where anyone who pulls it can read it.
# `.qits-maven-settings.xml` reads the two variables as the `qits-maven-network` server.
#
# -Dnative flips quarkus.native.enabled (service/pom.xml carries its own `native` profile). Cap
# native-image RAM so the build is deterministic on small machines.
#
# -DskipTests skips execution, not compilation: test sources still compile here. Running them is what
# is not possible in this stage — the suite spawns an embedded postgres, and PackagedSurfaceIT (which
# the `native` profile un-skips) launches the binary against one. `./mvnw verify` on a clone is the
# gate for those.
RUN --mount=type=secret,id=qits-client-id,env=QITS_MAVEN_AUTH_USR \
    --mount=type=secret,id=qits-client-secret,env=QITS_MAVEN_AUTH_PSW \
    ./mvnw -B -ntp -s .qits-maven-settings.xml -pl service -am package -Dnative -DskipTests \
      -Dqits.maven.repository.url="$QITS_MAVEN_REPOSITORY_URL" \
      -Dquarkus.native.native-image-xmx=4g

# --- runtime --------------------------------------------------------------------------------------
# `mirror.dev.localhost:8080/redhat/…` is the same mirror, pointed at registry.access.redhat.com.
FROM mirror.dev.localhost:8080/redhat/ubi9/ubi-minimal:9.6
WORKDIR /work

# Nothing is installed here. This service shells out to nothing at all — the binary and libc are the
# whole runtime. If that ever stops being true, the missing executable will surface as an IOException
# at the first request rather than at build time, so add the package in the same commit as the code
# that forks it.

# `quarkus.package.output-name` names the binary `qits-configuration` (no -runner suffix), so the
# process is identifiable in `ps`/`docker top`. It is built by `service`, the one module of the two
# that is an application; `configuration` is a library jar with nothing to native-compile.
COPY --from=build --chown=1001:root /src/service/target/qits-configuration /work/qits-configuration
RUN chmod +x /work/qits-configuration

# NO MOUNT POINT FOR DATA. The store is another container's: this service writes nothing to disk that
# outlives it, so there is no volume to own and none to back up. The durability this component exists
# to provide is the postgres one.

EXPOSE 8080
USER 1001

# Everything this service serves hangs off /configuration — /configuration/api/* and
# /configuration/q/* — because qits-gateway routes verbatim by prefix. The paths are in
# application.properties, not here; see README.md.
ENTRYPOINT ["/work/qits-configuration", "-Dquarkus.http.host=0.0.0.0"]
