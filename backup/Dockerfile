FROM amazonlinux:2027@sha256:94adffec30a5fe0f6c81b8f47bbf961f265b191e6d178a51a45c3bbd9232f6e7

RUN yum update && yum install -y postgresql16 awscli jq

COPY backup.sh /backup.sh

CMD ["/backup.sh"]
