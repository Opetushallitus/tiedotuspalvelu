const environments = ["hahtuva", "dev", "qa", "prod"] as const;
type EnvironmentName = (typeof environments)[number];

export type AutoScalingLimits = {
  min: number;
  max: number;
};
export type Config = {
  oauthDomainName: string;
  opintopolkuHost: string;
  virkailijaHost: string;
  apiCapacity: AutoScalingLimits;
  batchCapacity: AutoScalingLimits;
  tiedotuspalveluCapacity: AutoScalingLimits;
  features: {
    vtj: boolean;
    "tiedotuspalvelu.fetch-oppija.enabled": boolean;
    "tiedotuspalvelu.suomifi-viestit.enabled": boolean;
    "tiedotuspalvelu.suomifi-viestit.base-url": string;
  };
};

const defaultConfig = {
  apiCapacity: { min: 2, max: 8 },
  batchCapacity: { min: 1, max: 1 },
  tiedotuspalveluCapacity: { min: 1, max: 1 },
  features: {
    vtj: true,
    "tiedotuspalvelu.fetch-oppija.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
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
  oauthDomainName: "hahtuva.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "hahtuvaopintopolku.fi",
  virkailijaHost: "virkailija.hahtuvaopintopolku.fi",
  apiCapacity: { min: 1, max: 2 },
  features: {
    vtj: false,
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
  },
};

export const dev: Config = {
  ...defaultConfig,
  oauthDomainName: "dev.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "untuvaopintopolku.fi",
  virkailijaHost: "virkailija.untuvaopintopolku.fi",
  apiCapacity: { min: 1, max: 2 },
  features: {
    vtj: false,
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.enabled": false,
    "tiedotuspalvelu.suomifi-viestit.base-url": "http://localhost",
  },
};

export const qa: Config = {
  ...defaultConfig,
  oauthDomainName: "qa.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "testiopintopolku.fi",
  virkailijaHost: "virkailija.testiopintopolku.fi",
  apiCapacity: { min: 1, max: 2 },
  features: {
    vtj: false,
    "tiedotuspalvelu.fetch-oppija.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.enabled": true,
    "tiedotuspalvelu.suomifi-viestit.base-url":
      "https://api.messages-qa.suomi.fi",
  },
};

export const prod: Config = {
  ...defaultConfig,
  oauthDomainName: "prod.tiedotuspalvelu.opintopolku.fi",
  opintopolkuHost: "opintopolku.fi",
  virkailijaHost: "virkailija.opintopolku.fi",
  apiCapacity: { min: 2, max: 8 },
  tiedotuspalveluCapacity: { min: 0, max: 0 },
  features: {
    ...defaultConfig.features,
  },
};
