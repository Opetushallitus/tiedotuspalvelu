FROM maven:3.10.0-amazoncorretto-21-al2023@sha256:c287cc32273f94ac4ab07ba68c92809374db696633307ae06887d902f4bfb93e AS build

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

FROM amazoncorretto:25.0.5@sha256:15a7151d73f5d0472263aeb091e0d6f4e8e72be0e5c7b0b94a09cf58de369caf
WORKDIR /app

COPY --from=build /app/target/tiedotuspalvelu-1.0.0.jar application.jar
COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
