package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

public interface HenkiloTableLoader {
  long load(String bucketName, String objectKey);
}
