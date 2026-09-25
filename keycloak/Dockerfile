FROM quay.io/keycloak/keycloak:26.7.4@sha256:82a77884f3af238beab1e7afd63b5f530e1b5c0590bd7aa60b40a40463e29b2c

ADD --chmod=644 \
  --checksum=sha256:54d3f82b90caf4f7ca630957eda8d70524d7140cb510e9939a962bcee545655c \
https://github.com/jacekkow/keycloak-protocol-cas/releases/download/26.5.5/keycloak-protocol-cas-26.5.5.jar \
  /opt/keycloak/providers/keycloak-protocol-cas-26.5.5.jar
