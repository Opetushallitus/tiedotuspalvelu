import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as iam from "aws-cdk-lib/aws-iam";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as rds from "aws-cdk-lib/aws-rds";
import * as elasticloadbalancingv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as route53_targets from "aws-cdk-lib/aws-route53-targets";
import * as sns from "aws-cdk-lib/aws-sns";
import * as sns_subscriptions from "aws-cdk-lib/aws-sns-subscriptions";
import * as ssm from "aws-cdk-lib/aws-ssm";
import * as certificatemanager from "aws-cdk-lib/aws-certificatemanager";
import * as ecr_assets from "aws-cdk-lib/aws-ecr-assets";
import * as logs from "aws-cdk-lib/aws-logs";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import {getConfig, getEnvironment} from "./config";
import * as path from "node:path";
import {createHealthCheckStacks} from "./health-check";
import * as alarms from "./alarms";
import * as constants from "./constants";

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
    const {alarmsToSlackLambda, alarmTopic} = new AlarmStack(
      this,
      "AlarmStack",
      stackProps,
    );
    const {vpc, bastion} = new VpcStack(this, "VpcStack", stackProps);
    const ecsStack = new ECSStack(
      this,
      "ECSStack",
      vpc,
      stackProps,
    );
    // TODO: tiedotuspalvelu apparently doesn't use datantuonti for anything. Should it though? If not, remove these
    // const datantuontiExportStack = new datantuonti.ExportStack(
    //   this,
    //   sharedAccount.prefix("DatantuontiExport"),
    //   stackProps,
    // );
    const databaseStack = new TiedotusDatabaseStack(
      this,
      "Database",
      // ecsStack.cluster,
      // datantuontiExportStack.bucket,
      vpc,
      bastion,
      {...stackProps, alarmTopic},
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
      hostedZone: dnsStack.tiedotuspalveluHostedZone,
      alarmTopic,
      vpc,
    });
  }
}

class DnsStack extends cdk.Stack {
  // readonly oppijanumerorekisteriHostedZone: route53.IHostedZone;
  readonly tiedotuspalveluHostedZone: route53.IHostedZone;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    // this.oppijanumerorekisteriHostedZone = new route53.HostedZone(
    //   this,
    //   "HostedZone",
    //   {
    //     zoneName: config.oauthDomainName,
    //   },
    // );
    this.tiedotuspalveluHostedZone = new route53.HostedZone(
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

    // TODO: add PagerDutyIntegrationUrl to secret manager, create integration to PagerDuty for Tiedotuspalvelu
    // const pagerDutyIntegrationUrlSecret =
    //   secretsmanager.Secret.fromSecretNameV2(
    //     this,
    //     "PagerDutyIntegrationUrlSecret",
    //     "PagerDutyIntegrationUrl",
    //   );

    // this.alarmTopic.addSubscription(
    //   new sns_subscriptions.UrlSubscription(
    //     pagerDutyIntegrationUrlSecret.secretValue.toString(),
    //     { protocol: sns.SubscriptionProtocol.HTTPS },
    //   ),
    // );

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
      tags: [{key: "Name", value: id}],
    });
  }
}

class ECSStack extends cdk.Stack {
  public cluster: ecs.Cluster;

  constructor(scope: constructs.Construct, id: string, vpc: ec2.IVpc, props: cdk.StackProps) {
    super(scope, id, props);

    this.cluster = new ecs.Cluster(this, "Cluster", {
      vpc,
      clusterName: "Cluster",
    });
  }
}

class TiedotusDatabaseStack extends cdk.Stack {
  readonly database: rds.DatabaseCluster;

  // readonly exportBucket: s3.Bucket;

  constructor(
    scope: constructs.Construct,
    id: string,
    //ecsCluster: ecs.Cluster,
    //datantuontiExportBucket: s3.Bucket,
    vpc: ec2.IVpc,
    bastion: ec2.BastionHostLinux,
    props: cdk.StackProps & {
      alarmTopic: sns.ITopic;
    },
  ) {
    super(scope, id, props);

    // const datantuontiImportRole = new iam.Role(this, "DatantuontiImport", {
    //   assumedBy: new iam.ServicePrincipal("rds.amazonaws.com"),
    // });
    // datantuonti
    //   .createS3ImporPolicyStatements(this)
    //   .forEach((statement) => datantuontiImportRole.addToPolicy(statement));

    // this.exportBucket = new s3.Bucket(this, "ExportBucket", {});

    // this.database = new rds.DatabaseCluster(
    //   this,
    //   "TiedotuspalveluDatabase",
    //   {
    //     vpc,
    //     vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
    //     defaultDatabaseName: "tiedotuspalvelu",
    //     engine: rds.DatabaseClusterEngine.auroraPostgres({
    //       version: rds.AuroraPostgresEngineVersion.VER_16_4,
    //     }),
    //     credentials: rds.Credentials.fromGeneratedSecret("tiedotuspalvelu", {
    //       secretName: sharedAccount.prefix("TiedotuspalveluDatabaseSecret"),
    //     }),
    //     storageType: rds.DBClusterStorageType.AURORA,
    //     writer: rds.ClusterInstance.provisioned("writer", {
    //       enablePerformanceInsights: true,
    //       instanceType: ec2.InstanceType.of(
    //         ec2.InstanceClass.T4G,
    //         ec2.InstanceSize.MEDIUM,
    //       ),
    //     }),
    //     storageEncrypted: true,
    //     readers: [],
    //   },
    // );

    const dbClusterProps: rds.DatabaseClusterProps = {
      vpc,
      vpcSubnets: {subnetType: ec2.SubnetType.PRIVATE_ISOLATED},
      defaultDatabaseName: "tiedotuspalvelu",
      engine: rds.DatabaseClusterEngine.auroraPostgres({
        version: rds.AuroraPostgresEngineVersion.VER_16_4,
      }),
      credentials: rds.Credentials.fromGeneratedSecret("tiedotuspalvelu", {
        secretName: "DatabaseSecret",
      }),
      storageType: rds.DBClusterStorageType.AURORA,
      writer: rds.ClusterInstance.provisioned("writer", {
        enablePerformanceInsights: true,
        instanceType: ec2.InstanceType.of(
          ec2.InstanceClass.T4G,
          ec2.InstanceSize.MEDIUM,
        ),
      }),
      // storageEncrypted: true,
      readers: [],
      // TODO: should db cluster have the s3 buckets?
      // s3ExportBuckets: [this.exportBucket, datantuontiExportBucket],
      // s3ExportRole: datantuontiImportRole,
    };

    // TODO: do we want to encrypt data in the db also in hahtuva and dev?
    if (getEnvironment() == "hahtuva" || getEnvironment() == "dev") {
      this.database = new rds.DatabaseCluster(this, "Database", {
        ...dbClusterProps,
      });
    } else {
      // Prod cluster
      this.database = new rds.DatabaseCluster(this, "Database", {
        ...dbClusterProps,
        storageEncrypted: true,
      });
    }

    this.database.connections.allowDefaultPortFrom(bastion);

    // TODO: should therer be something similar for tiedotuspalvelu db?
    // const backup = new DatabaseBackupToS3(this, "DatabaseBackupToS3", {
    //   ecsCluster: ecsCluster,
    //   dbCluster: this.database,
    //   dbName: "oppijanumerorekisteri",
    //   alarmTopic: props.alarmTopic,
    // });
    // this.database.connections.allowDefaultPortFrom(backup);
  }
}

type TiedotuspalveluStackProps = cdk.StackProps & {
  ecsCluster: ecs.Cluster;
  hostedZone: route53.IHostedZone;
  database: rds.DatabaseCluster;
  alarmTopic: sns.ITopic;
  vpc: ec2.IVpc;
  // TODO: should this stack also have the buckets for export?
  // exportBucket: s3.Bucket;
  // datantuontiExportBucket: s3.Bucket;
  // datantuontiExportEncryptionKey: kms.IKey;
};

class TiedotuspalveluStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    props: TiedotuspalveluStackProps,
  ) {
    super(scope, id, props);

    const domainForNginxForwarding = `nginx.${config.tiedotuspalveluDomain}`;

    const logGroup = new logs.LogGroup(this, "AppLogGroup", {
      logGroupName: "Tiedotuspalvelu/tiedotuspalvelu",
      retention: logs.RetentionDays.INFINITE,
    });

    if (config.tiedotuspalveluCapacity.max > 0) {
      if (config.features["tiedotuspalvelu.fetch-oppija.enabled"]) {
        this.fetchOppijaAlarm(logGroup, props.alarmTopic);
      }
      if (config.features["tiedotuspalvelu.suomifi-viestit.enabled"]) {
        this.sendSuomiFiViestitAlarm(logGroup, props.alarmTopic);
        this.fetchSuomiFiViestitEventsAlarm(logGroup, props.alarmTopic);
      }
      this.fetchLocalisationsAlarm(logGroup, props.alarmTopic);
      this.casClientSessionCleanerAlarm(logGroup, props.alarmTopic);
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
      logging: new ecs.AwsLogDriver({logGroup, streamPrefix: "app"}),
      environment: {
        ENV: getEnvironment(),
        "server.port": appPort.toString(),
        "tiedotuspalvelu.oppija-origin": `https://${config.opintopolkuHost}`,
        "tiedotuspalvelu.virkailija-origin": `https://${config.virkailijaHost}`,
        "tiedotuspalvelu.api-base-url": `https://${domainForNginxForwarding}`,
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
      // TODO: should the parameter and secret names be changed, since we are using tiedotuspalvelu AWS account?
      secrets: {
        "tiedotuspalvelu.otuva.oauth2-client-id": ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromSecureStringParameterAttributes(
            this,
            "TiedotuspalveluOauth2ClientId",
            {parameterName: "/oauth2/client-id"},
          ),
        ),
        "tiedotuspalvelu.otuva.oauth2-client-secret":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "TiedotuspalveluOauth2ClientSecret",
              {parameterName: "/oauth2/client-secret"},
            ),
          ),
        "tiedotuspalvelu.suomifi-viestit.username": ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromSecureStringParameterAttributes(
            this,
            "TiedotuspalveluSuomifiViestitUsername",
            {parameterName: "/suomifi-viestit/username"},
          ),
        ),
        "tiedotuspalvelu.suomifi-viestit.password": ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromSecureStringParameterAttributes(
            this,
            "TiedotuspalveluSuomifiViestitPassword",
            {parameterName: "/suomifi-viestit/password"},
          ),
        ),
        "tiedotuspalvelu.suomifi-viestit.sender-service-id":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "TiedotuspalveluSuomifiViestitSenderServiceId",
              {parameterName: "/suomifi-viestit/sender-service-id"},
            ),
          ),
        "tiedotuspalvelu.suomifi-viestit.posti.username":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "TiedotuspalveluSuomifiViestitPostiUsername",
              {
                parameterName:
                  "/suomifi-viestit/posti-username",
              },
            ),
          ),
        "tiedotuspalvelu.suomifi-viestit.posti.password":
          ecs.Secret.fromSsmParameter(
            ssm.StringParameter.fromSecureStringParameterAttributes(
              this,
              "TiedotuspalveluSuomifiViestitPostiPassword",
              {
                parameterName:
                  "/suomifi-viestit/posti-password",
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

    const service = new ecs.FargateService(this, "Service", {
      cluster: props.ecsCluster,
      taskDefinition,
      desiredCount: config.tiedotuspalveluCapacity.min,
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      vpcSubnets: {subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS},
      healthCheckGracePeriod: cdk.Duration.minutes(5),
      circuitBreaker: {enable: true},
    });

    const scaling = service.autoScaleTaskCount({
      minCapacity: config.tiedotuspalveluCapacity.min,
      maxCapacity: config.tiedotuspalveluCapacity.max,
    });

    scaling.scaleOnMetric("ServiceScaling", {
      metric: service.metricCpuUtilization(),
      scalingSteps: [
        {upper: 15, change: -1},
        {lower: 50, change: +1},
        {lower: 65, change: +2},
        {lower: 80, change: +3},
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

    new route53.ARecord(this, "ARecord", {
      zone: props.hostedZone,
      recordName: config.tiedotuspalveluDomain,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(alb),
      ),
    });
    new route53.ARecord(this, "NginxARecord", {
      zone: props.hostedZone,
      recordName: domainForNginxForwarding,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(alb),
      ),
    });

    const nginxCertificate = new certificatemanager.Certificate(
      this,
      "AlbNginxCertificate",
      {
        domainName: domainForNginxForwarding,
        subjectAlternativeNames: [config.tiedotuspalveluDomain],
        validation: certificatemanager.CertificateValidation.fromDns(
          props.hostedZone,
        ),
      },
    );

    const listener = alb.addListener("Listener", {
      protocol: elasticloadbalancingv2.ApplicationProtocol.HTTPS,
      port: 443,
      open: true,
      certificates: [nginxCertificate],
    });

    listener.addTargets("ServiceTarget", {
      port: appPort,
      targets: [service],
      healthCheck: {
        enabled: true,
        interval: cdk.Duration.seconds(30),
        path: "/omat-viestit/actuator/health",
        port: appPort.toString(),
      },
    });

  }

  fetchOppijaAlarm(logGroup: logs.LogGroup, alarmTopic: sns.ITopic) {
    alarms.alarmIfExpectedLogLineIsMissing(
      this,
      "FetchOppijaTask",
      logGroup,
      alarmTopic,
      logs.FilterPattern.literal('"Finished running FetchOppijaTask"'),
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
}

const app = new CdkApp({
  defaultStackSynthesizer: new cdk.DefaultStackSynthesizer({
    qualifier: constants.CDK_QUALIFIER,
  }),
});
app.synth();
