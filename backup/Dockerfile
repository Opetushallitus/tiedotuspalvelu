FROM amazonlinux:2027@sha256:7d1d4fbfb37449500694f6ca10d2e4e9c66a197075f021063d930cd7eb547fe3

RUN yum update && yum install -y postgresql16 awscli jq

COPY backup.sh /backup.sh

CMD ["/backup.sh"]
