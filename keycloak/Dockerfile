FROM quay.io/keycloak/keycloak:26.7.0@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13

ADD --chmod=644 \
  --checksum=sha256:54d3f82b90caf4f7ca630957eda8d70524d7140cb510e9939a962bcee545655c \
https://github.com/jacekkow/keycloak-protocol-cas/releases/download/26.5.5/keycloak-protocol-cas-26.5.5.jar \
  /opt/keycloak/providers/keycloak-protocol-cas-26.5.5.jar
