package org.symphonyoss.s2.fugue.cicd.v1

import java.util.Map.Entry

/**
 * Class to execute FugeDeploy, creating roles and other pre-requisites if necessary.
 * 
 * @author Bruce Skingle
 *
 */
class FugueDeploy implements Serializable
{
  private def     steps_
  private String  task_
  private String  logGroup_
  private String  awsRegion_
  private String  dockerLabel_  = 'latest'


  private String  awsAccount_
  private String  cluster_
  private String  configGitOrg_
  private String  configGitRepo_
  private String  configGitBranch_

  private String  environmentType_
  private String  environment_
  private String  realm_
  private String  region_
  private String  servicename_
  private String  tenantId_
  private boolean primaryEnvironment_
  private boolean primaryRegion_
  private String  configTaskdef_
  private String  role_
  private String  executionRole_  = 'ecsTaskExecutionRole'
  private String  memory_         = '1024'
  private String  cpu_            = '256'
  private String  port_           = '80'
  private String  consulTokenId_
  private String  accountId_
  
  public FugueDeploy(steps, task, logGroup, awsAccount, cluster, awsRegion)
  {
      steps_ = steps
      task_ = task
      logGroup_ = logGroup
      awsAccount_ = awsAccount;
      cluster_ = cluster
      awsRegion_ = awsRegion
  }
  
  public FugueDeploy withServiceName(String n)
  {
      servicename_ = n
      
      return this
  }
  
  public FugueDeploy withConfigGitRepo(String org, String repo, String branch = 'master')
  {
    configGitOrg_ = org
    configGitRepo_ = repo
    configGitBranch_ = branch
    
    return this
  }
  
  public FugueDeploy withDockerLabel(String s)
  {
    dockerLabel_ = s
    
    return this
  }
  
  public FugueDeploy withTenantId(String s)
  {
    tenantId_ = s
    
    return this
  }
  
  public FugueDeploy withAccountId(String s)
  {
    accountId_ = s
    
    return this
  }
  
  public FugueDeploy withStation(Station s)
  {
    environmentType_    = s.environmentType
    environment_        = s.environment
    realm_              = s.realm
    region_             = s.region
    primaryEnvironment_ = s.primaryEnvironment
    primaryRegion_      = s.primaryRegion
    role_               = environmentType_ + '-' + environment_ + '-admin-role'
    consulTokenId_      = 'sym-consul-' + environmentType_
    accountId_          = 'fugue-' + environmentType_ + '-cicd'
    
    return this
  }
  
  public FugueDeploy withEnvironmentType(String s)
  {
    environmentType_    = s
    
    return this
  }
  
  public FugueDeploy withEnvironment(String s)
  {
    environment_    = s
    
    return this
  }
  
  public FugueDeploy withRealm(String s)
  {
    realm_    = s
    
    return this
  }
  
  public FugueDeploy withRegion(String s)
  {
    region_    = s
    
    return this
  }

  public FugueDeploy withRole(String s)
  {
    role_    = s
    
    return this
  }

  public void execute()
  {
    steps_.echo """
------------------------------------------------------------------------------------------------------------------------
FugeDeploy execute start
------------------------------------------------------------------------------------------------------------------------
"""
    
    String taskRoleArn    = 'arn:aws:iam::' + awsAccount_ + ':role/' + role_
    String executionRoleArn    = 'arn:aws:iam::' + awsAccount_ + ':role/' + executionRole_
    String taskDefFamily  = 'fugue-deploy-' + environmentType_ + '-' + environment_
    String serviceImage   = awsAccount_ + '.dkr.ecr.us-east-1.amazonaws.com/fugue/fugue-deploy:' + dockerLabel_

    String consulToken
    String gitHubToken
    
    if(consulTokenId_ != null)
    {
      steps_.withCredentials([steps_.string(credentialsId: consulTokenId_, variable: 'CONSUL_TOKEN')])
      {
        consulToken = steps_.sh(returnStdout:true, script:'echo -n $CONSUL_TOKEN').trim()
      }
    }
    
    steps_.withCredentials([steps_.string(credentialsId: 'symphonyjenkinsauto-token', variable: 'GITHUB_TOKEN')])
    {
      gitHubToken = steps_.sh(returnStdout:true, script:'echo -n $GITHUB_TOKEN').trim()
    }
    
    configTaskdef_ =
    '''{

    "executionRoleArn": "''' + executionRoleArn + '''",
    "taskRoleArn": "''' + taskRoleArn + '''",
    "family": "fugue-deploy",
    "networkMode": "awsvpc", 
    "memory": "''' + memory_ + '''",
    "cpu": "''' + cpu_ + '''", 
    "requiresCompatibilities": [
        "FARGATE"
    ], 
    "containerDefinitions": [
        {
            "name": "''' + taskDefFamily + '''",
            "image": "''' + serviceImage + '''",
            "essential": true,
            "portMappings": [
                {
                    "containerPort": ''' + port_ + ''',
                    "protocol": "tcp"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "''' + logGroup_ + '''",
                    "awslogs-region": "''' + awsRegion_ + '''",
                    "awslogs-stream-prefix": "''' + taskDefFamily + '''"
                }
            },
            "environment": [
                {
                    "name": "AWS_REGION",
                    "value": "''' + awsRegion_ + '''"
                }'''
                    
    addIfNotNull("FUGUE_ENVIRONMENT_TYPE", environmentType_)
    addIfNotNull("FUGUE_ENVIRONMENT", environment_)
    addIfNotNull("FUGUE_REALM", realm_)
    addIfNotNull("FUGUE_REGION", region_)
    addIfNotNull("FUGUE_SERVICE", servicename_)
    addIfNotNull("FUGUE_ACTION", task_)
    addIfNotNull("FUGUE_TENANT", tenantId_)
    addIfNotNull("FUGUE_PRIMARY_ENVIRONMENT", primaryEnvironment_)
    addIfNotNull("FUGUE_PRIMARY_REGION", primaryRegion_)
    
    if(environmentType_.equals('smoke'))
      addIfNotNull("CONSUL_URL", "https://consul-dev.symphony.com:8080")
    else
      addIfNotNull("CONSUL_URL", "https://consul-" + environmentType_ + ".symphony.com:8080")
    addIfNotNull("CONSUL_TOKEN", consulToken)
    addIfNotNull("GITHUB_TOKEN", gitHubToken)
    addIfNotNull("GITHUB_ORG", configGitOrg_)
    addIfNotNull("GITHUB_REPO", configGitRepo_)
    addIfNotNull("GITHUB_BRANCH", configGitBranch_)
    
    configTaskdef_ = configTaskdef_ + '''
            ]
        }
    ]
}'''

    steps_.echo 'configTaskdef_=' + configTaskdef_
    
    steps_.withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
      credentialsId: accountId_,
      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']])
    {
      def taskdef_file = 'ecs-' + environment_ + '-' + tenantId_ + '.json'
      steps_.writeFile file:taskdef_file, text:configTaskdef_
      
      steps_.sh 'aws --region us-east-1 ecs register-task-definition --cli-input-json file://'+taskdef_file+' > ecs-taskdef-out-' + environment_ + '-' + tenantId_ + '.json'

      steps_.sh 'aws sts get-caller-identity'
      
      def taskRun = steps_.readJSON(text:
        steps_.sh(returnStdout: true, script: 'aws --region us-east-1 ecs run-task --cluster ' + cluster_ +
        ' --launch-type fargate' +
        ' --network-configuration awsvpcConfiguration={subnets=[' + steps_.environmentTypeConfig[environmentType].subnets_ +
           '],securityGroups=[' + steps_.environmentTypeConfig[environmentType].securityGroups_ +
           '],assignPublicIp=DISABLED}' +
        ' --task-definition fugue-deploy --count 1'
        )
      )
        
      steps_.echo """
Task run
taskArn: ${taskRun.tasks[0].taskArn}
lastStatus: ${taskRun.tasks[0].lastStatus}
"""
      String taskArn = taskRun.tasks[0].taskArn
      String taskId = taskArn.substring(taskArn.lastIndexOf('/')+1)
      
      steps_.sh 'aws --region us-east-1 ecs wait tasks-stopped --cluster ' + cluster_ +
        ' --tasks ' + taskArn
      

      def taskDescription = steps_.readJSON(text:
        steps_.sh(returnStdout: true, script: 'aws --region us-east-1 ecs describe-tasks  --cluster ' + 
          cluster_ +
          ' --tasks ' + taskArn
        )
      )
      
      steps_.echo """
Task run
taskArn: ${taskDescription.tasks[0].taskArn}
lastStatus: ${taskDescription.tasks[0].lastStatus}
stoppedReason: ${taskDescription.tasks[0].stoppedReason}
exitCode: ${taskDescription.tasks[0].containers[0].exitCode}
"""
      //TODO: only print log if failed...
      steps_.sh 'aws --region us-east-1 logs get-log-events --log-group-name fugue' +
      ' --log-stream-name ' + taskDefFamily + '/' + taskDefFamily + '/' + taskId + ' | fgrep "message" | sed -e \'s/ *"message": "//\' | sed -e \'s/"$//\' | sed -e \'s/\\\\t/      /\''
      if(taskDescription.tasks[0].containers[0].exitCode != 0) {
        
        throw new IllegalStateException('Init task fugue-deploy failed with exit code ' + taskDescription.tasks[0].containers[0].exitCode)
      }
    }
    
    
    steps_.echo """
------------------------------------------------------------------------------------------------------------------------
FugeDeploy execute finish
------------------------------------------------------------------------------------------------------------------------
"""
  }
  
  private void addIfNotNull(String name, Object value)
  {
    //steps_.echo 'name=' + name + ', value=' + value
    
    if(value != null)
    {
      configTaskdef_ = configTaskdef_ + ''',
                {
                    "name": "''' + name + '''",
                    "value": "''' + value + '''"
                }'''
    }
  }
}
