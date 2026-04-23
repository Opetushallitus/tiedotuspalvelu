const environments = ["hahtuva", "dev", "qa", "prod"] as const;
type EnvironmentName = (typeof environments)[number];

export type AutoScalingLimits = {
  min: number;
  max: number;
};
export type Config = {
  dnsDelegated: boolean;
  oauthDomainName: string;
  opintopolkuHost: string;
  virkailijaHost: string;
  tiedotuspalveluCapacity: AutoScalingLimits;
  features: {
    vtj: boolean;
    "tiedotuspalvelu.fetch-oppija.enabled": boolean;
    "tiedotuspalvelu.suomifi-viestit.enabled": boolean;
    "tiedotuspalvelu.suomifi-viestit.base-url": string;
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": boolean;
  };
};

const defaultConfig = {
  dnsDelegated: true,
  tiedotuspalveluCapacity: { min: 0, max: 0 },
  features: {
    vtj: true,
    "tiedotuspalvelu.fetch-oppija.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": true,
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
  dnsDelegated: false,
  oauthDomainName: "hahtuva.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "hahtuvaopintopolku.fi",
  virkailijaHost: "virkailija.hahtuvaopintopolku.fi",
  features: {
    vtj: false,
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": false,
  },
};

export const dev: Config = {
  ...defaultConfig,
  dnsDelegated: false,
  oauthDomainName: "dev.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "untuvaopintopolku.fi",
  virkailijaHost: "virkailija.untuvaopintopolku.fi",
  features: {
    vtj: false,
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": true,
  },
};

export const qa: Config = {
  ...defaultConfig,
  dnsDelegated: false,
  oauthDomainName: "qa.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "testiopintopolku.fi",
  virkailijaHost: "virkailija.testiopintopolku.fi",
  features: {
    vtj: false,
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.base-url":
      "https://api.messages-qa.suomi.fi",
    "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": true,
  },
};

export const prod: Config = {
  ...defaultConfig,
  dnsDelegated: false,
  oauthDomainName: "prod.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "opintopolku.fi",
  virkailijaHost: "virkailija.opintopolku.fi",
  features: {
    ...defaultConfig.features,
  },
};
