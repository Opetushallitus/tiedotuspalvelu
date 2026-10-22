FROM maven:3.10.0-amazoncorretto-21-al2023@sha256:67d01e49c4c824e5a0d444414e4825b79dcbbbd40f41e47bc9b4c8e8c84a3217 AS build

RUN dnf install -y nodejs24 \
  && alternatives --install /usr/bin/node node /usr/bin/node-24 90 \
  && alternatives --install /usr/bin/npm npm /usr/bin/npm-24 90 \
  && alternatives --install /usr/bin/npx npx /usr/bin/npx-24 90

WORKDIR /app
COPY . .

WORKDIR /app/web
RUN npm ci
RUN npx webpack build

WORKDIR /app
RUN mvn --batch-mode clean package -s codebuild-mvn-settings.xml -DskipTests

FROM amazoncorretto:25.0.4@sha256:769a8f4f7e400098cf4eea22ed56e5690527346044c17dfcc7f6b4c42e105a78
WORKDIR /app

COPY --from=build /app/target/tiedotuspalvelu-1.0.0.jar application.jar
COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
