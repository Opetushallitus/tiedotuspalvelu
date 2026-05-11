FROM quay.io/keycloak/keycloak:26.6.1@sha256:dea26401d06341095cc4ea9d66896200b55de5ca1daa1d2fcbe58493afa6e0ad

ADD --chmod=644 \
  --checksum=sha256:54d3f82b90caf4f7ca630957eda8d70524d7140cb510e9939a962bcee545655c \
https://github.com/jacekkow/keycloak-protocol-cas/releases/download/26.5.5/keycloak-protocol-cas-26.5.5.jar \
  /opt/keycloak/providers/keycloak-protocol-cas-26.5.5.jar
