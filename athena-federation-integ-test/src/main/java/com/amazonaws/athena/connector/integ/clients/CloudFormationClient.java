private class CloudFormationClient
{
    private static final Logger logger = LoggerFactory.getLogger(CloudFormationClient.class);

    private static final String CF_CREATE_RESOURCE_IN_PROGRESS_STATUS = "CREATE_IN_PROGRESS";
    private static final String CF_CREATE_RESOURCE_FAILED_STATUS = "CREATE_FAILED";
    private static final long sleepTimeMillis = 5000L;

    private final String stackName;
    private final String stackTemplate;
    private final AmazonCloudFormation cloudFormationClient;

    public CloudFormationClient(String stackName, String stackTemplate, AmazonCloudFormation cloudFormationClient)
    {
        this.stackName = stackName;
        this.stackTemplate = stackTemplate;
        this.cloudFormationClient = cloudFormationClient;
    }

    private final String stackName;
    private final String stackTemplate;
    private final AmazonCloudFormation cloudFormationClient;

<<<<<<< HEAD
    public CloudFormationClient(Pair<Stack, App> stackPair)
=======
    public CloudFormationClient(String stackName, String stackTemplate, AmazonCloudFormation cloudFormationClient)
>>>>>>> origin/master
    {
        this(stackPair.first(), stackPair.second());
    }

    public CloudFormationClient(Stack theStack, App theApp)
    {
        stackName = this.stackName = stackName;
        ObjectMapper objectMapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
        stackTemplate = objectMapper
                .valueToTree(theApp.synth().getStackArtifact(theStack.getArtifactId()).getTemplate())
                .toPrettyString();
        this.cloudFormationClient = AmazonCloudFormationClientBuilder.standard().withCredentials(credentialsProvider).build();
    }

    /**
     * Creates a CloudFormation stack to build the infrastructure needed to run the integration tests (e.g., Database
     * instance, Lambda function, etc...). Once the stack is created successfully, the lambda function is registered
     * with Athena.
     */
    public void createStack() throws Exception
    {
        logger.info("------------------------------------------------------");
        logger.info("Create CloudFormation stack: {}", stackName);
        logger.info("------------------------------------------------------");
        logger.info(stackTemplate);

        CreateStackRequest createStackRequest = new CreateStackRequest()
                .withStackName(stackName)
                .withTemplateBody(stackTemplate)
                .withDisableRollback(true)
                .withCapabilities(Capability.CAPABILITY_NAMED_IAM);
        processCreateStackRequest(createStackRequest);
    }

    /**
     * Processes the creation of a CloudFormation stack including polling of the stack's status while in progress, adds appropriate error handling and logging, and throws RuntimeException
     * @param createStackRequest Request used to generate the CloudFormation stack.
     * @throws RuntimeException The CloudFormation stack creation failed.
     */
    private void processCreateStackRequest(CreateStackRequest createStackRequest)
            throws Exception
    {
        // Create CloudFormation stack.
        CreateStackResult result = cloudFormationClient.createStack(createStackRequest);
        logger.info("Stack ID: {}", result.getStackId());

        DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest()
                .withStackName(createStackRequest.getStackName());
        DescribeStackEventsResult describeStackEventsResult;

        // Poll status of stack until stack has been created or creation has failed
        while (true) {
                .withDisableRollback(true)
                .withCapabilities(Capability.CAPABILITY_NAMED_IAM);
            describeStackEventsResult = cloudFormationClient.describeStackEvents(describeStackEventsRequest);
            StackEvent event = describeStackEventsResult.getStackEvents().get(0);
            String resourceId = event.getLogicalResourceId();
            String resourceStatus = event.getResourceStatus();
            logger.info("Resource Id: {}, Resource status: {}", resourceId, resourceStatus);
            if (!resourceId.equals(event.getStackName()) ||
                    resourceStatus.equals(CF_CREATE_RESOURCE_IN_PROGRESS_STATUS)) {
                try {
                    Thread.sleep(sleepTimeMillis);
                    continue;
                }
                catch (InterruptedException e) {
                    throw new RuntimeException("Thread.sleep interrupted: " + e.getMessage(), e);
                }
            }
            else if (resourceStatus.equals(CF_CREATE_RESOURCE_FAILED_STATUS)) {
                throw new RuntimeException(getCloudFormationErrorReasons(describeStackEventsResult.getStackEvents()) + "\n Stack Events: " + describeStackEventsResult.getStackEvents());
            }
            break;
        }
                .withDisableRollback(true)
                .withCapabilities(Capability.CAPABILITY_NAMED_IAM);
    }

    /**
     * Provides a detailed error message when the CloudFormation stack creation fails.
     * @param stackEvents The list of CloudFormation stack events.
     * @return String containing the formatted error message.
     */
    private String getCloudFormationErrorReasons(List<StackEvent> stackEvents)
    {
        StringBuilder errorMessageBuilder =
                new StringBuilder("CloudFormation stack creation failed due to the following reason(s):\n");

        stackEvents.forEach(stackEvent -> {
            if (stackEvent.getResourceStatus().equals(CF_CREATE_RESOURCE_FAILED_STATUS)) {
                String errorMessage = String.format("Resource: %s, Reason: %s\n",
                        stackEvent.getLogicalResourceId(), stackEvent.getResourceStatusReason());
                errorMessageBuilder.append(errorMessage);
            }
        });

        return errorMessageBuilder.toString();
    }

    /**
     * Deletes a CloudFormation stack, and the lambda function registered with Athena.
     */
    public void deleteStack()
    {
        logger.info("------------------------------------------------------");
        logger.info("Delete CloudFormation stack: {}", stackName);
        logger.info("------------------------------------------------------");

        try {
            DeleteStackRequest request = new DeleteStackRequest().withStackName(stackName);
            cloudFormationClient.deleteStack(request);
        }
        catch (Exception e) {
            logger.error("Something went wrong... Manual resource cleanup may be needed!!!", e);
        }
        finally {
            cloudFormationClient.shutdown();
        }
    }
}
