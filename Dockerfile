FROM maven:3.9.16-amazoncorretto-21-al2023@sha256:f959c046a4013e2dc5e6a25272f69a42414dc8d08c42f4a2cab8f78c64226ddb AS build

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

FROM amazoncorretto:25.0.4@sha256:f0049986a5be7e9edd4c2cf27d28bfd5fec51aea20dd0d5bfda748f43a2595a8
WORKDIR /app

COPY --from=build /app/target/tiedotuspalvelu-1.0.0.jar application.jar
COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
