import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as cloudtrail from "aws-cdk-lib/aws-cloudtrail";
import * as cloudwatch from "aws-cdk-lib/aws-cloudwatch";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as iam from "aws-cdk-lib/aws-iam";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as rds from "aws-cdk-lib/aws-rds";
import * as elasticloadbalancingv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as route53_targets from "aws-cdk-lib/aws-route53-targets";
import * as sns from "aws-cdk-lib/aws-sns";
import * as certificatemanager from "aws-cdk-lib/aws-certificatemanager";
import * as sns_subscriptions from "aws-cdk-lib/aws-sns-subscriptions";
import * as ssm from "aws-cdk-lib/aws-ssm";
import * as ecr_assets from "aws-cdk-lib/aws-ecr-assets";
import * as logs from "aws-cdk-lib/aws-logs";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import * as s3 from "aws-cdk-lib/aws-s3";
import { getConfig, getEnvironment } from "./config";
import * as path from "node:path";
import { createHealthCheckStacks } from "./health-check";
import * as alarms from "./alarms";
import { wireAlarmToSnsTopic } from "./alarms";
import * as constants from "./constants";
import { ResponseAlarms } from "./response-alarms";
import { SecurityAlertsStack } from "./security-alerts";

const config = getConfig();

class CdkApp extends cdk.App {
  constructor(props: cdk.AppProps) {
    super(props);
    const stackProps = {
      env: {
        account: process.env.CDK_DEPLOY_TARGET_ACCOUNT,
        region: process.env.CDK_DEPLOY_TARGET_REGION,
      },
    };

    const dnsStack = new DnsStack(this, "DnsStack", stackProps);
    const { alarmsToSlackLambda, alarmTopic } = new AlarmStack(
      this,
      "AlarmStack",
      stackProps,
    );
    const { vpc, bastion } = new VpcStack(this, "VpcStack", stackProps);
    const ecsStack = new ECSStack(this, "ECSStack", vpc, stackProps);

    const databaseStack = new TiedotusDatabaseStack(
      this,
      "Database",
      vpc,
      bastion,
      { ...stackProps, alarmTopic },
    );

    createHealthCheckStacks(this, alarmsToSlackLambda, [
      {
        name: "Tiedotuspalvelu",
        url: new URL(
          `https://${config.opintopolkuHost}/omat-viestit/actuator/health`,
        ),
      },
    ]);

    new TiedotuspalveluStack(this, "TiedotuspalveluApplication", {
      ...stackProps,
      database: databaseStack.database,
      ecsCluster: ecsStack.cluster,
      hostedZone: dnsStack.hostedZone,
      alarmTopic,
      vpc,
    });

    if (
      config.features["tiedotuspalvelu.security.infra-changes-alerts.enabled"]
    ) {
      new SecurityAlertsStack(this, "TiedotuspalveluSecurityAlarms", {
        ...stackProps,
        alarmTopic,
      });
    }
  }
}

class DnsStack extends cdk.Stack {
  readonly hostedZone: route53.IHostedZone;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.hostedZone = new route53.HostedZone(
      this,
      "TiedotuspalveluHostedZone",
      {
        zoneName: config.tiedotuspalveluDomain,
      },
    );
  }
}

class AlarmStack extends cdk.Stack {
  readonly alarmTopic: sns.ITopic;
  readonly alarmsToSlackLambda: lambda.IFunction;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.alarmsToSlackLambda = this.createAlarmsToSlackLambda();
    this.alarmTopic = this.createAlarmTopic();

    this.alarmTopic.addSubscription(
      new sns_subscriptions.LambdaSubscription(this.alarmsToSlackLambda),
    );

    const pagerDutyIntegrationUrlSecret =
      secretsmanager.Secret.fromSecretNameV2(
        this,
        "PagerDutyIntegrationUrlSecret",
        "PagerDutyIntegrationUrl",
      );

    this.alarmTopic.addSubscription(
      new sns_subscriptions.UrlSubscription(
        pagerDutyIntegrationUrlSecret.secretValue.toString(),
        { protocol: sns.SubscriptionProtocol.HTTPS },
      ),
    );

    const radiatorReader = new iam.Role(this, "RadiatorReaderRole", {
      assumedBy: new iam.AccountPrincipal(constants.RADIATOR_ACCOUNT_ID),
      roleName: "RadiatorReader",
    });
    radiatorReader.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ["cloudwatch:DescribeAlarms"],
        resources: ["*"],
      }),
    );

    this.exportValue(this.alarmTopic.topicArn);
  }

  createAlarmTopic() {
    return new sns.Topic(this, "AlarmTopic", {});
  }

  createAlarmsToSlackLambda() {
    const alarmsToSlack = new lambda.Function(this, "AlarmsToSlack", {
      code: lambda.Code.fromAsset("../alarms-to-slack"),
      handler: "alarms-to-slack.handler",
      runtime: new lambda.Runtime("nodejs24.x"),
      architecture: lambda.Architecture.ARM_64,
      timeout: cdk.Duration.seconds(30),
    });

    // https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_lambda.html
    const parametersAndSecretsExtension =
      lambda.LayerVersion.fromLayerVersionArn(
        this,
        "ParametersAndSecretsLambdaExtension",
        "arn:aws:lambda:eu-west-1:015030872274:layer:AWS-Parameters-and-Secrets-Lambda-Extension-Arm64:11",
      );

    alarmsToSlack.addLayers(parametersAndSecretsExtension);
    secretsmanager.Secret.fromSecretNameV2(
      this,
      "slack-webhook",
      "slack-webhook",
    ).grantRead(alarmsToSlack);

    return alarmsToSlack;
  }
}

class VpcStack extends cdk.Stack {
  readonly vpc: ec2.IVpc;
  readonly bastion: ec2.BastionHostLinux;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);
    this.vpc = this.createVpc();
    this.bastion = this.createBastion();
  }

  createVpc() {
    const outIpAddresses = this.createOutIpAddresses();
    const natProvider = ec2.NatProvider.gateway({
      eipAllocationIds: outIpAddresses.map((ip) =>
        ip.getAtt("AllocationId").toString(),
      ),
    });
    const vpc = new ec2.Vpc(this, "Vpc", {
      vpcName: "vpc",
      subnetConfiguration: [
        {
          name: "Ingress",
          subnetType: ec2.SubnetType.PUBLIC,
        },
        {
          name: "Application",
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
        {
          name: "Database",
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
        },
      ],
      maxAzs: 3,
      natGateways: 3,
      natGatewayProvider: natProvider,
    });
    vpc.addGatewayEndpoint("S3Endpoint", {
      service: ec2.GatewayVpcEndpointAwsService.S3,
    });
    return vpc;
  }

  private createBastion(): ec2.BastionHostLinux {
    return new ec2.BastionHostLinux(this, "Bastion", {
      vpc: this.vpc,
      instanceName: "Bastion",
      requireImdsv2: true,
    });
  }

  private createOutIpAddresses() {
    // Ainakin Oiva näitä IP-osoitteita rajaamaan pääsyä palvelun rajapintoihin
    return ["OutIpAddress1", "OutIpAddress2", "OutIpAddress3"].map((ip) =>
      this.createIpAddress(ip),
    );
  }

  private createIpAddress(id: string) {
    return new ec2.CfnEIP(this, id, {
      tags: [{ key: "Name", value: id }],
    });
  }
}

class ECSStack extends cdk.Stack {
  public cluster: ecs.Cluster;

  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    this.cluster = new ecs.Cluster(this, "Cluster", {
      vpc,
      clusterName: "Cluster",
    });
  }
}

class TiedotusDatabaseStack extends cdk.Stack {
  readonly database: rds.DatabaseCluster;

  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    bastion: ec2.BastionHostLinux,
    props: cdk.StackProps & {
      alarmTopic: sns.ITopic;
    },
  ) {
    super(scope, id, props);

    const s3ImportRole = new iam.Role(this, "DbS3ImportRole", {
      assumedBy: new iam.ServicePrincipal("rds.amazonaws.com"),
    });
    grantOppijanumerorekisteriExportRead(s3ImportRole);

    this.database = new rds.DatabaseCluster(this, "DatabaseCluster", {
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      defaultDatabaseName: "tiedotuspalvelu",
      engine: rds.DatabaseClusterEngine.auroraPostgres({
        version: rds.AuroraPostgresEngineVersion.VER_16_4,
      }),
      credentials: rds.Credentials.fromGeneratedSecret("tiedotuspalvelu", {
        secretName: "TiedotuspalveluDatabaseSecret",
      }),
      storageType: rds.DBClusterStorageType.AURORA,
      storageEncrypted: true,
      s3ImportRole: s3ImportRole,
      writer: rds.ClusterInstance.provisioned("writer", {
        enablePerformanceInsights: true,
        instanceType: ec2.InstanceType.of(
          ec2.InstanceClass.T4G,
          ec2.InstanceSize.MEDIUM,
        ),
      }),
      readers: [],
    });
    this.database.connections.allowDefaultPortFrom(bastion);
  }
}

type TiedotuspalveluStackProps = cdk.StackProps & {
  ecsCluster: ecs.Cluster;
  hostedZone: route53.IHostedZone;
  database: rds.DatabaseCluster;
  alarmTopic: sns.ITopic;
  vpc: ec2.IVpc;
};

class TiedotuspalveluStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    props: TiedotuspalveluStackProps,
  ) {
    super(scope, id, props);

    const subdomainForVirkailijaNginxForwarding = `nginx.${config.tiedotuspalveluDomain}`;

    const logGroup = new logs.LogGroup(this, "AppLogGroup", {
      logGroupName: "Tiedotuspalvelu/tiedotuspalvelu",
      retention: logs.RetentionDays.INFINITE,
    });
    this.koskiErrorsAlarm(logGroup, props.alarmTopic);
    this.apiLatencyMetric(logGroup);

    if (config.tiedotuspalveluCapacity.max > 0) {
      if (config.features["tiedotuspalvelu.fetch-oppija.enabled"]) {
        this.validateTiedoteAlarm(logGroup, props.alarmTopic);
      }
      if (config.features["tiedotuspalvelu.suomifi-viestit.enabled"]) {
        this.sendSuomiFiViestitAlarm(logGroup, props.alarmTopic);
        this.fetchSuomiFiViestitEventsAlarm(logGroup, props.alarmTopic);
      }
      this.fetchLocalisationsAlarm(logGroup, props.alarmTopic);
      this.casClientSessionCleanerAlarm(logGroup, props.alarmTopic);
      if (config.features["tiedotuspalvelu.henkilo-import.enabled"]) {
        this.henkiloImportAlarm(logGroup, props.alarmTopic);
      }

      new OutgoingRequestMonitoring(this, "OutgoingRequestMonitoring", {
        logGroup,
        alarmTopic: props.alarmTopic,
        clients: ["lokalisointi", "oppijanumerorekisteri", "suomifi-viestit"],
      });
    }

    const dockerImage = new ecr_assets.DockerImageAsset(this, "AppImage", {
      directory: path.join(__dirname, "../.."),
      file: "Dockerfile",
      platform: ecr_assets.Platform.LINUX_ARM64,
      exclude: ["infra/cdk.out"],
    });

    const taskDefinition = new ecs.FargateTaskDefinition(
      this,
      "TaskDefinition",
      {
        cpu: 2048,
        memoryLimitMiB: 5120,
        runtimePlatform: {
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
        },
      },
    );
    const koskiRoleArn = ssm.StringParameter.valueFromLookup(
      this,
      "koski-role-arn",
    );

    const appPort = 8080;
    taskDefinition.addContainer("AppContainer", {
      image: ecs.ContainerImage.fromDockerImageAsset(dockerImage),
      logging: new ecs.AwsLogDriver({ logGroup, streamPrefix: "app" }),
      environment: {
        ENV: getEnvironment(),
        "server.port": appPort.toString(),
        "tiedotuspalvelu.oppija-origin": `https://${config.opintopolkuHost}`,
        "tiedotuspalvelu.virkailija-origin": `https://${config.virkailijaHost}`,
        "tiedotuspalvelu.api-base-url": `https://${config.tiedotuspalveluDomain}`,
        "tiedotuspalvelu.opintopolku-host": config.opintopolkuHost,
        "tiedotuspalvelu.oppijanumerorekisteri.base-url": `https://${getEnvironment()}.oppijanumerorekisteri.opintopolku.fi/oppijanumerorekisteri-service`,
        "tiedotuspalvelu.fetch-oppija.enabled": `${config.features["tiedotuspalvelu.fetch-oppija.enabled"]}`,
        "tiedotuspalvelu.suomifi-viestit.enabled": `${config.features["tiedotuspalvelu.suomifi-viestit.enabled"]}`,
        "tiedotuspalvelu.suomifi-viestit.base-url": `${config.features["tiedotuspalvelu.suomifi-viestit.base-url"]}`,
        "tiedotuspalvelu.otuva.oauth2-token-url": `https://${getEnvironment()}.otuva.opintopolku.fi/kayttooikeus-service/oauth2/token`,
        "tiedotuspalvelu.swagger-ui.oauth2-token-url": `https://${getEnvironment()}.otuva.opintopolku.fi/kayttooikeus-service/oauth2/token`,
        "spring.security.oauth2.resourceserver.jwt.issuer-uri": `https://${getEnvironment()}.otuva.opintopolku.fi/kayttooikeus-service`,
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri": `https://${getEnvironment()}.otuva.opintopolku.fi/kayttooikeus-service/oauth2/jwks`,
        "spring.datasource.url": `jdbc:postgresql://${props.database.clusterEndpoint.hostname}:${props.database.clusterEndpoint.port}/tiedotuspalvelu`,
        "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled": `${config.features["tiedotuspalvelu.fetch-kielitutkintotodistus.enabled"]}`,
        "tiedotuspalvelu.henkilo-import.enabled": `${config.features["tiedotuspalvelu.henkilo-import.enabled"]}`,
        "tiedotuspalvelu.henkilo-import.bucket-name":
          config.oppijanumerorekisteri.exportBucket,
        "tiedotuspalvelu.koski-role-arn": koskiRoleArn,
        "tiedotuspalvelu.suomifi-viestit.posti.contact-email":
          ssm.StringParameter.valueForStringParameter(
            this,
            "/suomifi-viestit/posti-contact-email",
          ),
        "tiedotuspalvelu.suomifi-viestit.sender-address.name":
          ssm.StringParameter.valueForStringParameter(
            this,
            "/suomifi-viestit/sender-address-name",
          ),
        "tiedotuspalvelu.suomifi-viestit.sender-address.street-address":
          ssm.StringParameter.valueForStringParameter(
            this,
            "/suomifi-viestit/sender-address-street-address",
          ),
        "tiedotuspalvelu.suomifi-viestit.sender-address.zip-code":
          ssm.StringParameter.valueForStringParameter(
            this,
            "/suomifi-viestit/sender-address-zip-code",
          ),
        "tiedotuspalvelu.suomifi-viestit.sender-address.city":
          ssm.StringParameter.valueForStringParameter(
            this,
            "/suomifi-viestit/sender-address-city",
          ),
        "tiedotuspalvelu.suomifi-viestit.sender-address.country-code":
          ssm.StringParameter.valueForStringParameter(
            this,
            "/suomifi-viestit/sender-address-country-code",
          ),
      },
      secrets: {
        "tiedotuspalvelu.otuva.oauth2-client-id": ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromSecureStringParameterAttributes(
            this,
            "Oauth2ClientId",
            { parameterName: "/oauth2/client-id" },
          ),
        ),
        "tiedotuspalvelu.otuva.oauth2-client-secret":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "Oauth2ClientSecret",
              { parameterName: "/oauth2/client-secret" },
            ),
          ),
        "tiedotuspalvelu.suomifi-viestit.username": ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromSecureStringParameterAttributes(
            this,
            "SuomifiViestitUsername",
            { parameterName: "/suomifi-viestit/username" },
          ),
        ),
        "tiedotuspalvelu.suomifi-viestit.password": ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromSecureStringParameterAttributes(
            this,
            "SuomifiViestitPassword",
            { parameterName: "/suomifi-viestit/password" },
          ),
        ),
        "tiedotuspalvelu.suomifi-viestit.sender-service-id":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "SuomifiViestitSenderServiceId",
              { parameterName: "/suomifi-viestit/sender-service-id" },
            ),
          ),
        "tiedotuspalvelu.suomifi-viestit.posti.username":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "SuomifiViestitPostiUsername",
              {
                parameterName: "/suomifi-viestit/posti-username",
              },
            ),
          ),
        "tiedotuspalvelu.suomifi-viestit.posti.password":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "SuomifiViestitPostiPassword",
              {
                parameterName: "/suomifi-viestit/posti-password",
              },
            ),
          ),
        "spring.datasource.username": ecs.Secret.fromSecretsManager(
          props.database.secret!,
          "username",
        ),
        "spring.datasource.password": ecs.Secret.fromSecretsManager(
          props.database.secret!,
          "password",
        ),
      },
      portMappings: [
        {
          name: "app",
          containerPort: appPort,
          appProtocol: ecs.AppProtocol.http,
        },
      ],
    });

    taskDefinition.addToTaskRolePolicy(
      new iam.PolicyStatement({
        actions: ["sts:AssumeRole"],
        resources: [koskiRoleArn],
      }),
    );

    grantOppijanumerorekisteriExportRead(taskDefinition.taskRole);

    const service = new ecs.FargateService(this, "Service", {
      cluster: props.ecsCluster,
      taskDefinition,
      desiredCount: config.tiedotuspalveluCapacity.min,
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      healthCheckGracePeriod: cdk.Duration.minutes(5),
      circuitBreaker: { enable: true },
    });

    const scaling = service.autoScaleTaskCount({
      minCapacity: config.tiedotuspalveluCapacity.min,
      maxCapacity: config.tiedotuspalveluCapacity.max,
    });

    scaling.scaleOnMetric("ServiceScaling", {
      metric: service.metricCpuUtilization(),
      scalingSteps: [
        { upper: 15, change: -1 },
        { lower: 50, change: +1 },
        { lower: 65, change: +2 },
        { lower: 80, change: +3 },
      ],
    });

    service.connections.allowToDefaultPort(props.database);

    const alb = new elasticloadbalancingv2.ApplicationLoadBalancer(
      this,
      "LoadBalancer",
      {
        vpc: props.vpc,
        internetFacing: true,
      },
    );

    this.addAlbAccessLogging(alb);

    new route53.ARecord(this, "ARecord", {
      zone: props.hostedZone,
      recordName: config.tiedotuspalveluDomain,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(alb),
      ),
    });
    new route53.ARecord(this, "NginxARecord", {
      zone: props.hostedZone,
      recordName: subdomainForVirkailijaNginxForwarding,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(alb),
      ),
    });
    const certificate = new certificatemanager.Certificate(
      this,
      "Certificate",
      {
        domainName: config.tiedotuspalveluDomain,
        subjectAlternativeNames: [subdomainForVirkailijaNginxForwarding],
        validation: certificatemanager.CertificateValidation.fromDns(
          props.hostedZone,
        ),
      },
    );
    const listener = alb.addListener("Listener", {
      protocol: elasticloadbalancingv2.ApplicationProtocol.HTTPS,
      port: 443,
      open: true,
      certificates: [certificate],
      ...(config.features["tiedotuspalvelu.alb.tls13pq.enabled"]
        ? { sslPolicy: elasticloadbalancingv2.SslPolicy.TLS13_12_RES_PQ }
        : {}),
    });
    const target = listener.addTargets("ServiceTarget", {
      port: appPort,
      targets: [service],
      healthCheck: {
        enabled: true,
        interval: cdk.Duration.seconds(30),
        path: "/omat-viestit/actuator/health",
        port: appPort.toString(),
      },
    });
    new ResponseAlarms(this, "ResponseAlarms", {
      prefix: "Tiedotuspalvelu",
      alarmTopic: props.alarmTopic,
      alb,
      albThreshold: 1,
      target,
      targetThreshold: 1,
    });
  }

  validateTiedoteAlarm(logGroup: logs.LogGroup, alarmTopic: sns.ITopic) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "ValidateTiedoteTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal('"Finished running ValidateTiedoteTask"'),
    );
  }

  sendSuomiFiViestitAlarm(logGroup: logs.LogGroup, alarmTopic: sns.ITopic) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "SendSuomiFiViestitTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal('"Finished running SendSuomiFiViestitTask"'),
    );
  }

  fetchSuomiFiViestitEventsAlarm(
    logGroup: logs.LogGroup,
    alarmTopic: sns.ITopic,
  ) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "FetchSuomiFiViestitEventsTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal(
        '"Finished running FetchSuomiFiViestitEventsTask"',
      ),
    );
  }

  fetchLocalisationsAlarm(logGroup: logs.LogGroup, alarmTopic: sns.ITopic) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "FetchLocalisationsTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal('"Finished running FetchLocalisationsTask"'),
    );
  }

  henkiloImportAlarm(logGroup: logs.LogGroup, alarmTopic: sns.ITopic) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "HenkiloImportTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal('"Finished running HenkiloImportTask"'),
      cdk.Duration.hours(2),
      1,
    );
  }

  casClientSessionCleanerAlarm(
    logGroup: logs.LogGroup,
    alarmTopic: sns.ITopic,
  ) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "CasClientSessionCleanerTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal(
        '"Finished running CasClientSessionCleanerTask"',
      ),
      cdk.Duration.hours(2),
      1,
    );
  }

  koskiErrorsAlarm(logGroup: logs.LogGroup, alarmTopic: sns.ITopic) {
    const koskiHenkiloOidForAlarms = ssm.StringParameter.valueFromLookup(
      this,
      "koski-henkilo-oid-for-alarms",
      "-",
    );
    if (koskiHenkiloOidForAlarms === "-") {
      return;
    }

    const filters = [
      {
        metricName: `RequestsFromKoski2XXCount`,
        pattern: logs.FilterPattern.stringValue("$.responseCode", "=", "2*"),
      },
      {
        metricName: `RequestsFromKoski4XXCount`,
        pattern: logs.FilterPattern.stringValue("$.responseCode", "=", "4*"),
        alarmProps: {
          comparisonOperator:
            cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
          threshold: 1,
        },
      },
      {
        metricName: `RequestsFromKoski5XXCount`,
        pattern: logs.FilterPattern.stringValue("$.responseCode", "=", "5*"),
        alarmProps: {
          comparisonOperator:
            cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
          threshold: 1,
        },
      },
    ];
    for (const { metricName, pattern, alarmProps } of filters) {
      const metricFilter = logGroup.addMetricFilter(`${metricName}Filter`, {
        metricNamespace: "Tiedotuspalvelu",
        metricName: `${metricName}`,
        filterPattern: logs.FilterPattern.all(
          this.isApiEndpoint(),
          this.fromCaller(koskiHenkiloOidForAlarms),
          pattern,
        ),
        metricValue: "1",
      });
      if (alarmProps) {
        const alarm = metricFilter
          .metric({
            period: cdk.Duration.minutes(5),
            statistic: "Sum",
          })
          .createAlarm(this, `${metricName}Alarm`, {
            alarmName: `Tiedotuspalvelu${metricName}Alarm`,
            treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
            evaluationPeriods: 1,
            ...alarmProps,
          });
        wireAlarmToSnsTopic(alarm, alarmTopic);
      }
    }
  }

  apiLatencyMetric(logGroup: logs.LogGroup) {
    logGroup.addMetricFilter("ApiResponseTimeFilter", {
      metricNamespace: "Tiedotuspalvelu",
      metricName: "ApiResponseTime",
      filterPattern: logs.FilterPattern.all(
        this.isApiEndpoint(),
        logs.FilterPattern.numberValue("$.responseTime", ">=", 0),
      ),
      metricValue: "$.responseTime",
      dimensions: { requestMapping: "$.requestMapping" },
    });
  }

  isApiEndpoint() {
    return logs.FilterPattern.stringValue(
      "$.requestMapping",
      "=",
      "/omat-viestit/api/*",
    );
  }

  fromCaller(callerHenkiloOid: string) {
    return logs.FilterPattern.stringValue(
      "$.callerHenkiloOid",
      "=",
      callerHenkiloOid,
    );
  }

  addAlbAccessLogging(alb: elasticloadbalancingv2.ApplicationLoadBalancer) {
    if (config.features["tiedotuspalvelu.alb.accessLogging.enabled"]) {
      const albAccessLogsBucket = new s3.Bucket(
        this,
        "LoadBalancerAccessLogsBucket",
        {
          bucketName: `tiedotus-alb-access-logs-${this.account}-${this.region}`,
          encryption: s3.BucketEncryption.S3_MANAGED,
          blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
          enforceSSL: true,
          lifecycleRules: [
            {
              id: "ExpireAfter5Years",
              enabled: true,
              expiration: cdk.Duration.days(config.albAccessLogsExpirationDays),
            },
          ],
          removalPolicy: cdk.RemovalPolicy.RETAIN,
        },
      );

      alb.logAccessLogs(albAccessLogsBucket);
    }
  }
}

class OutgoingRequestMonitoring extends constructs.Construct {
  constructor(
    scope: constructs.Construct,
    id: string,
    props: {
      logGroup: logs.ILogGroup;
      alarmTopic: sns.ITopic;
      clients: string[];
    },
  ) {
    super(scope, id);

    const alarms = [
      {
        statusClass: "4XX",
        name: "4XX",
        threshold: 10,
      },
      {
        statusClass: "5XX",
        name: "5XX",
        threshold: 1,
      },
      {
        statusClass: "Other",
        name: "NoStatus",
        threshold: 1,
      },
    ];

    props.clients.forEach((client) => {
      alarms.forEach(({ statusClass, name, threshold }) => {
        const alarm = new cloudwatch.Alarm(
          this,
          `OutgoingRequest${name}Alarm-${client}`,
          {
            alarmName: `TiedotuspalveluOutgoingRequest${name}Alarm-${client}`,
            evaluationPeriods: 1,
            threshold,
            treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
            metric: new cloudwatch.Metric({
              namespace: "Tiedotuspalvelu",
              metricName: "latency",
              statistic: "SampleCount",
              period: cdk.Duration.minutes(30),
              dimensionsMap: {
                client: client,
                statusClass: statusClass,
              },
            }),
          },
        );
        wireAlarmToSnsTopic(alarm, props.alarmTopic);
      });
    });
  }
}

function grantOppijanumerorekisteriExportRead(grantee: iam.IGrantable): void {
  [
    new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ["s3:GetObject"],
      resources: [
        `arn:aws:s3:::${config.oppijanumerorekisteri.exportBucket}/fulldump/henkilo/v1/*`,
      ],
    }),
    new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ["s3:ListBucket"],
      resources: [`arn:aws:s3:::${config.oppijanumerorekisteri.exportBucket}`],
      conditions: {
        StringLike: { "s3:prefix": ["fulldump/henkilo/v1/*"] },
      },
    }),
    new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ["kms:Decrypt"],
      resources: [config.oppijanumerorekisteri.exportKeyArn],
    }),
  ].forEach((policy) => grantee.grantPrincipal.addToPrincipalPolicy(policy));
}

const app = new CdkApp({
  defaultStackSynthesizer: new cdk.DefaultStackSynthesizer({
    qualifier: constants.CDK_QUALIFIER,
  }),
});
app.synth();
