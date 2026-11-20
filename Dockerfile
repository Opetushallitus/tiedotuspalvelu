FROM maven:3.10.0-amazoncorretto-21-al2023@sha256:9f817dbf24e058d433223e72d59c4f89cc9ca4634f053a9e40debfe09a234ffa AS build

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

FROM amazoncorretto:25.0.5@sha256:288b5647146a21027688b52cc23d802ee6badb6f28632111b606b79262d9fc5f
WORKDIR /app

COPY --from=build /app/target/tiedotuspalvelu-1.0.0.jar application.jar
COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
