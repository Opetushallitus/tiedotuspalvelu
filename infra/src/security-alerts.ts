import * as constructs from "constructs";
import * as cdk from "aws-cdk-lib";
import * as cloudtrail from "aws-cdk-lib/aws-cloudtrail";
import * as cloudwatch from "aws-cdk-lib/aws-cloudwatch";
import * as cloudwatch_actions from "aws-cdk-lib/aws-cloudwatch-actions";
import * as logs from "aws-cdk-lib/aws-logs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as sns from "aws-cdk-lib/aws-sns";

interface SecurityAlertProps {
  logGroup: logs.ILogGroup;
  filterName: string;
  filterPattern: string;
  alertDescription: string;
  snsTopic: sns.ITopic;
  namespace?: string;
}

class SecurityAlert extends constructs.Construct {
  public readonly alarm: cloudwatch.Alarm;

  constructor(
    scope: constructs.Construct,
    id: string,
    props: SecurityAlertProps,
  ) {
    super(scope, id);

    const namespace = props.namespace ?? "SecurityAlerts";

    const metricFilter = new logs.MetricFilter(this, "Filter", {
      logGroup: props.logGroup,
      filterPattern: logs.FilterPattern.literal(props.filterPattern),
      metricNamespace: namespace,
      metricName: props.filterName,
      metricValue: "1",
      defaultValue: 0,
    });

    this.alarm = new cloudwatch.Alarm(this, "Alarm", {
      alarmName: props.filterName,
      alarmDescription: props.alertDescription,
      metric: metricFilter.metric({
        statistic: "Sum",
        period: cdk.Duration.minutes(5),
      }),
      threshold: 1,
      evaluationPeriods: 1,
      comparisonOperator:
        cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    this.alarm.addAlarmAction(new cloudwatch_actions.SnsAction(props.snsTopic));
    this.alarm.addOkAction(new cloudwatch_actions.SnsAction(props.snsTopic));
  }
}

interface SecurityAlertsStackProps extends cdk.StackProps {
  logRetentionDays?: logs.RetentionDays;
  alarmTopic: sns.ITopic;
}

export class SecurityAlertsStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    props: SecurityAlertsStackProps,
  ) {
    super(scope, id, props);

    const logGroup = new logs.LogGroup(
      this,
      "SecurityAlertsCloudTrailLogGroup",
      {
        logGroupName: "/aws/cloudtrail/tiedotus-security-alerts",
        retention: props.logRetentionDays ?? logs.RetentionDays.ONE_YEAR,
        removalPolicy: cdk.RemovalPolicy.RETAIN,
      },
    );

    const trailBucket = new s3.Bucket(this, "SecurityAlertsTrailBucket", {
      bucketName: `tiedotus-security-alerts-${this.account}-${this.region}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      versioned: true,
      lifecycleRules: [{ expiration: cdk.Duration.days(365) }],
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    new cloudtrail.Trail(this, "SecurityTrail", {
      trailName: "security-audit-trail",
      bucket: trailBucket,
      cloudWatchLogGroup: logGroup,
      sendToCloudWatchLogs: true,
      isMultiRegionTrail: true,
      includeGlobalServiceEvents: true,
      enableFileValidation: true,
      managementEvents: cloudtrail.ReadWriteType.ALL,
    });

    this.createAlerts(logGroup, props.alarmTopic);
  }

  createAlerts(logGroup: logs.LogGroup, alertTopic: sns.ITopic) {
    this.createAlertForIamPolicyChanges(logGroup, alertTopic);
    this.createAlertForCloudTrailChanges(logGroup, alertTopic);
    this.createAlertForKmsCmkChanges(logGroup, alertTopic);
    this.createAlertForS3BucketPolicyChanges(logGroup, alertTopic);
    this.createAlertForSecurityGroupChanges(logGroup, alertTopic);
    this.createAlertForNetworkGatewayChanges(logGroup, alertTopic);
    this.createAlertForRouteTableChanges(logGroup, alertTopic);
    this.createAlertForVpcChanges(logGroup, alertTopic);
    this.createAlertForParameterStoreChanges(logGroup, alertTopic);
  }

  createAlertForIamPolicyChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "IAMPolicyChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraIAMPolicyChanges",
      filterPattern:
        "{ ($.eventName=DeleteGroupPolicy) || ($.eventName=DeleteRolePolicy) || ($.eventName=DeleteUserPolicy) || ($.eventName=PutGroupPolicy) || ($.eventName=PutRolePolicy) || ($.eventName=PutUserPolicy) || ($.eventName=CreatePolicy) || ($.eventName=DeletePolicy) || ($.eventName=CreatePolicyVersion) || ($.eventName=DeletePolicyVersion) || ($.eventName=SetDefaultPolicyVersion) || ($.eventName=AttachRolePolicy) || ($.eventName=DetachRolePolicy) || ($.eventName=AttachUserPolicy) || ($.eventName=DetachUserPolicy) || ($.eventName=AttachGroupPolicy) || ($.eventName=DetachGroupPolicy) }",
      alertDescription: "Tiedotuspalvelu - IAM policy changes detected",
      snsTopic: alertTopic,
    });
  }

  createAlertForCloudTrailChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "CloudTrailChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraCloudTrailChanges",
      filterPattern:
        "{ ($.eventName=CreateTrail) || ($.eventName=UpdateTrail) || ($.eventName=DeleteTrail) || ($.eventName=StartLogging) || ($.eventName=StopLogging) }",
      alertDescription: "Tiedotuspalvelu - CloudTrail configuration changed",
      snsTopic: alertTopic,
    });
  }

  createAlertForKmsCmkChanges(logGroup: logs.LogGroup, alertTopic: sns.ITopic) {
    new SecurityAlert(this, "KMSCMKChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraKMSCMKChanges",
      filterPattern:
        "{ ($.eventSource = kms.amazonaws.com) && (($.eventName=DisableKey) || ($.eventName=ScheduleKeyDeletion) || ($.eventName=DeleteImportedKeyMaterial) || ($.eventName=DisableKeyRotation)) }",
      alertDescription:
        "Tiedotuspalvelu - KMS CMK disabled or scheduled for deletion",
      snsTopic: alertTopic,
    });
  }

  createAlertForS3BucketPolicyChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "S3BucketPolicyChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraS3BucketPolicyChanges",
      filterPattern:
        "{ ($.eventSource = s3.amazonaws.com) && (($.eventName=PutBucketAcl) || ($.eventName=PutBucketPolicy) || ($.eventName=PutBucketCors) || ($.eventName=PutBucketLifecycle) || ($.eventName=PutBucketReplication) || ($.eventName=DeleteBucketPolicy) || ($.eventName=DeleteBucketCors) || ($.eventName=DeleteBucketLifecycle) || ($.eventName=DeleteBucketReplication)) }",
      alertDescription: "Tiedotuspalvelu - S3 bucket policy/ACL changed",
      snsTopic: alertTopic,
    });
  }

  createAlertForSecurityGroupChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "SecurityGroupChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraSecurityGroupChanges",
      filterPattern:
        "{ ($.eventName=AuthorizeSecurityGroupIngress) || ($.eventName=AuthorizeSecurityGroupEgress) || ($.eventName=RevokeSecurityGroupIngress) || ($.eventName=RevokeSecurityGroupEgress) || ($.eventName=CreateSecurityGroup) || ($.eventName=DeleteSecurityGroup) }",
      alertDescription: "Tiedotuspalvelu - Security group changed",
      snsTopic: alertTopic,
    });
  }

  createAlertForNetworkGatewayChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "NetworkGatewayChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraNetworkGatewayChanges",
      filterPattern:
        "{ ($.eventName=CreateCustomerGateway) || ($.eventName=DeleteCustomerGateway) || ($.eventName=AttachInternetGateway) || ($.eventName=CreateInternetGateway) || ($.eventName=DeleteInternetGateway) || ($.eventName=DetachInternetGateway) }",
      alertDescription: "Tiedotuspalvelu - Network gateway changed",
      snsTopic: alertTopic,
    });
  }

  createAlertForRouteTableChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "RouteTableChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraRouteTableChanges",
      filterPattern:
        "{ ($.eventName=CreateRoute) || ($.eventName=CreateRouteTable) || ($.eventName=ReplaceRoute) || ($.eventName=ReplaceRouteTableAssociation) || ($.eventName=DeleteRouteTable) || ($.eventName=DeleteRoute) || ($.eventName=DisassociateRouteTable) }",
      alertDescription: "Tiedotuspalvelu - Route table changed",
      snsTopic: alertTopic,
    });
  }

  createAlertForVpcChanges(logGroup: logs.LogGroup, alertTopic: sns.ITopic) {
    new SecurityAlert(this, "VPCChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraVPCChanges",
      filterPattern:
        "{ ($.eventName=CreateVpc) || ($.eventName=DeleteVpc) || ($.eventName=ModifyVpcAttribute) || ($.eventName=AcceptVpcPeeringConnection) || ($.eventName=CreateVpcPeeringConnection) || ($.eventName=DeleteVpcPeeringConnection) || ($.eventName=RejectVpcPeeringConnection) || ($.eventName=AttachClassicLinkVpc) || ($.eventName=DetachClassicLinkVpc) || ($.eventName=DisableVpcClassicLink) || ($.eventName=EnableVpcClassicLink) }",
      alertDescription: "Tiedotuspalvelu - VPC changed",
      snsTopic: alertTopic,
    });
  }

  createAlertForParameterStoreChanges(
    logGroup: logs.LogGroup,
    alertTopic: sns.ITopic,
  ) {
    new SecurityAlert(this, "SSMParameterStoreChanges", {
      logGroup,
      filterName: "TiedotuspalveluInfraSSMParameterStoreChanges",
      filterPattern:
        "{ ($.eventSource = ssm.amazonaws.com) && (($.eventName = PutParameter) || ($.eventName = DeleteParameter) || ($.eventName = DeleteParameters) || ($.eventName = LabelParameterVersion) || ($.eventName = RemoveTagsFromResource) || ($.eventName = AddTagsToResource)) }",
      alertDescription:
        "Tiedotuspalvelu - SSM Parameter Store parameter created, modified, deleted, or retagged",
      snsTopic: alertTopic,
    });
  }
}
