FROM amazonlinux:2027@sha256:ac5e5f7cb062949a53c5e4a570805bb847c87cc91fa3aa32c2f95d18e8ba1342

RUN yum update && yum install -y postgresql16 awscli jq

COPY backup.sh /backup.sh

CMD ["/backup.sh"]
