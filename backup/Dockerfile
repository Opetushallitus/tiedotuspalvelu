FROM amazonlinux:2027@sha256:c9dd22cd6f8ff381603bba041661582f2aaae8f5b6d06acfdb36db3818456524

RUN yum update && yum install -y postgresql16 awscli jq

COPY backup.sh /backup.sh

CMD ["/backup.sh"]
