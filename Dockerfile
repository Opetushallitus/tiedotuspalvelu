FROM maven:3.10.0-amazoncorretto-21-al2023@sha256:4afe14b2d5f5f9449c3109c6b71ec7585ac31b1f578d057c3cd81354af604ee0 AS build

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ENV NVM_DIR=/opt/nvm

COPY .nvmrc ./
COPY scripts/lib/nvm.sh ${NVM_DIR}/nvm.sh

RUN source "$NVM_DIR/nvm.sh" --no-use \
  && nvm install

WORKDIR /app
COPY . .

WORKDIR /app/web
RUN source "$NVM_DIR/nvm.sh" --no-use \
    && nvm use \
    && npm ci \
    && npx webpack build

WORKDIR /app
RUN mvn --batch-mode clean package -s codebuild-mvn-settings.xml -DskipTests

FROM amazoncorretto:25.0.5@sha256:ea0c335ad44c617fa977db26761e9126f4d2514bdc0a9a3f068e88622238c859
WORKDIR /app

COPY --from=build /app/target/tiedotuspalvelu-1.0.0.jar application.jar
COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
