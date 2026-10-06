FROM maven:3.10.0-amazoncorretto-21-al2023@sha256:9b868ffa88a46d8b4e605fa14db1e0debe2edb798b3b5890156e92d55a17b11b AS build

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

FROM amazoncorretto:25.0.4@sha256:c03c31e0e277996ddc4c80f0d674b7da29db3ab488b8b58c5d8f58b2abbe5d59
WORKDIR /app

COPY --from=build /app/target/tiedotuspalvelu-1.0.0.jar application.jar
COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
