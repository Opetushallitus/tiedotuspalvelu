FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b

ADD --chmod=644 \
  --checksum=sha256:54d3f82b90caf4f7ca630957eda8d70524d7140cb510e9939a962bcee545655c \
https://github.com/jacekkow/keycloak-protocol-cas/releases/download/26.5.5/keycloak-protocol-cas-26.5.5.jar \
  /opt/keycloak/providers/keycloak-protocol-cas-26.5.5.jar
