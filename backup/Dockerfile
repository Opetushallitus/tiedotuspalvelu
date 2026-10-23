FROM amazonlinux:2027@sha256:b2316d3e9d84e7d3b279ef8bfba78500c4419a9c733f5135607edb834f69e46a

RUN yum update && yum install -y postgresql16 awscli jq

COPY backup.sh /backup.sh

CMD ["/backup.sh"]
