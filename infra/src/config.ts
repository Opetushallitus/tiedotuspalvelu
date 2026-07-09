import * as constants from "./constants";

const environments = ["hahtuva", "dev", "qa", "prod"] as const;
type EnvironmentName = (typeof environments)[number];

export type AutoScalingLimits = {
  min: number;
  max: number;
};
export type Config = {
  tiedotuspalveluDomain: string;
  opintopolkuHost: string;
  virkailijaHost: string;
  tiedotuspalveluCapacity: AutoScalingLimits;
  oppijanumerorekisteri: {
    exportBucket: string;
    exportKeyArn: string;
  };
  albAccessLogsExpirationDays: number;
  features: {
    "tiedotuspalvelu.fetch-oppija.enabled": boolean;
    "tiedotuspalvelu.suomifi-viestit.enabled": boolean;
    "tiedotuspalvelu.suomifi-viestit.base-url": string;
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": boolean;
    "tiedotuspalvelu.henkilo-import.enabled": boolean;
    "tiedotuspalvelu.alb.tls13pq.enabled": boolean;
    "tiedotuspalvelu.alb.accessLogging.enabled": boolean;
    "tiedotuspalvelu.security.infra-changes-alerts.enabled": boolean;
  };
};

const defaultConfig = {
  tiedotuspalveluCapacity: { min: 2, max: 4 },
  albAccessLogsExpirationDays: constants.FIVE_YEARS_IN_DAYS,
  features: {
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": true,
    "tiedotuspalvelu.henkilo-import.enabled": true,
    "tiedotuspalvelu.alb.accessLogging.enabled": false,
    "tiedotuspalvelu.security.infra-changes-alerts.enabled": false,
  },
};

export function getEnvironment(): EnvironmentName {
  const env = process.env.ENV;
  if (!env) {
    throw new Error("ENV environment variable is not set");
  }
  if (!contains(environments, env)) {
    throw new Error(`Invalid environment name: ${env}`);
  }
  return env as EnvironmentName;
}

function contains(arr: readonly string[], value: string): boolean {
  return arr.includes(value);
}

export function getConfig(): Config {
  const env = getEnvironment();
  return { hahtuva, dev, qa, prod }[env];
}

export const hahtuva: Config = {
  ...defaultConfig,
  tiedotuspalveluDomain: "hahtuva.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "hahtuvaopintopolku.fi",
  virkailijaHost: "virkailija.hahtuvaopintopolku.fi",
  oppijanumerorekisteri: {
    exportBucket:
      "oppijanumerorekisteridatabase-exportbucket4e99310e-fg1jtjhmw1ak",
    exportKeyArn:
      "arn:aws:kms:eu-west-1:471112979851:key/1db3b719-53c6-4ecc-9249-08303b661684",
  },
  features: {
    ...defaultConfig.features,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": false,
    "tiedotuspalvelu.alb.tls13pq.enabled": true,
    "tiedotuspalvelu.security.infra-changes-alerts.enabled": true,
  },
};

export const dev: Config = {
  ...defaultConfig,
  tiedotuspalveluDomain: "dev.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "untuvaopintopolku.fi",
  virkailijaHost: "virkailija.untuvaopintopolku.fi",
  oppijanumerorekisteri: {
    exportBucket:
      "oppijanumerorekisteridatabase-exportbucket4e99310e-lwgbft1l0xt9",
    exportKeyArn:
      "arn:aws:kms:eu-west-1:058264235340:key/94f73e93-be42-460c-b543-11ae6576cc88",
  },
  features: {
    ...defaultConfig.features,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
    "tiedotuspalvelu.alb.tls13pq.enabled": true,
    "tiedotuspalvelu.security.infra-changes-alerts.enabled": true,
  },
};

export const qa: Config = {
  ...defaultConfig,
  tiedotuspalveluDomain: "qa.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "testiopintopolku.fi",
  virkailijaHost: "virkailija.testiopintopolku.fi",
  oppijanumerorekisteri: {
    exportBucket:
      "oppijanumerorekisteridatabase-exportbucket4e99310e-jvyilz0imgsj",
    exportKeyArn:
      "arn:aws:kms:eu-west-1:730335317715:key/685b53ef-f9e6-44b4-9bcf-d03f3acaa950",
  },
  features: {
    ...defaultConfig.features,
    "tiedotuspalvelu.suomifi-viestit.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.base-url":
      "https://api.messages-qa.suomi.fi",
    "tiedotuspalvelu.alb.tls13pq.enabled": true,
    "tiedotuspalvelu.security.infra-changes-alerts.enabled": true,
  },
};

export const prod: Config = {
  ...defaultConfig,
  tiedotuspalveluDomain: "prod.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "opintopolku.fi",
  virkailijaHost: "virkailija.opintopolku.fi",
  oppijanumerorekisteri: {
    exportBucket:
      "oppijanumerorekisteridatabase-exportbucket4e99310e-ycy54wagxhzo",
    exportKeyArn:
      "arn:aws:kms:eu-west-1:767397734142:key/ca376a09-004c-434d-89c0-81cb0aaaa2e8",
  },
  features: {
    ...defaultConfig.features,
    "tiedotuspalvelu.suomifi-viestit.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.base-url": "https://api.messages.suomi.fi",
    "tiedotuspalvelu.alb.tls13pq.enabled": false,
    "tiedotuspalvelu.alb.accessLogging.enabled": true,
  },
};
