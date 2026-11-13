# --- build ---
FROM eclipse-temurin:21-jdk AS build

ARG SBT_VERSION=1.12.11
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" | tar xz -C /opt \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/sbt/bin:${PATH}"

WORKDIR /app

COPY build.sbt .
COPY project/build.properties project/plugins.sbt project/
RUN sbt -batch update

COPY src src
RUN sbt -batch stage

# --- runtime ---
FROM eclipse-temurin:21-jre

RUN useradd --system --no-create-home vault
WORKDIR /app
COPY --from=build /app/target/universal/stage /app
USER vault

EXPOSE 8080
ENTRYPOINT ["/app/bin/password-vault-scala"]
