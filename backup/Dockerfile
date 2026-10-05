FROM amazonlinux:2027@sha256:6372f2b748bd5f284d1b1e1b059ab40c80bba32f5f0e95e30f8ef64d4d93b06e

RUN yum update && yum install -y postgresql16 awscli jq

COPY backup.sh /backup.sh

CMD ["/backup.sh"]
