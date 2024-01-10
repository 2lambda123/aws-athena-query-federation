#!/bin/bash

# Copyright (C) 2019 Amazon Web Services
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cat << EOF
# Run this script from the directory of the module (e.g. athena-example) that you wish to publish.
# This script performs the following actions:
# 1. Compile and build the Maven project
# 2. Generates a Serverless Application Package using the athena-example.yaml
# 3. Creates a final packaged.yaml that can be utilized to publish the application to your private Serverless Application Repository or deploy via CloudFormation
# private Serverless Application Repository or deployed via Cloudformation.
# 4. Uploads the packaged connector code to the specified S3 bucket
# 5. Grants Serverless Application Repository access to the connector code in S3 using sar_bucket_policy.json
# 6. Publishes the connector to the private Serverless Application Repository to enable 1-click deployment
EOF

while true; do
    read -p "Do you wish to proceed? (yes or no) " yn
    case $yn in
        [Yy]* ) echo "Proceeding..."; break;;
        [Nn]* ) exit;;
        * ) echo "Please answer yes or no.";;
    esac
done

if [ "$#" -lt 2 ]; then
    echo "\n\nERROR: Script requires 3 arguments \n"
    echo "\n1. S3_BUCKET used for publishing artifacts to Lambda/Serverless App Repo.\n"
    echo "\n2. The connector module to publish (e.g. athena-exmaple or athena-cloudwatch) \n"
    echo "\n3. The AWS REGION to target (e.g. us-east-1 or us-east-2) \n"
    echo "\n\n USAGE from the module's directory: ../tools/publish.sh my_s3_bucket athena-example \n"
    exit;
fi

if test -f "$2".yaml; then
    echo "SAR yaml found. We appear to be in the right directory."
else
  echo "SAR yaml not found, attempting to switch to module directory."
  cd $2
fi

REGION=$3
if [ -z "$REGION" ]
then
      REGION="us-east-1"
fi

echo "Using AWS Region $REGION"

if [[ $REGION == cn-* ]]; then
    PARTITION="aws-cn"
elif [[ $REGION == us-gov-* ]]; then
    PARTITION="aws-us-gov"
else
  PARTITION="aws"
fi

echo "Using PARTITION $PARTITION"


if ! aws s3api get-bucket-policy --bucket $1 --region $REGION| grep 'Statement' ; then
    echo "No bucket policy is set on $1 , would you like to add a Serverless Application Repository Bucket Policy?"
    while true; do
        read -p "Do you wish to proceed? (yes or no) " yn
        case $yn in
            [Yy]* ) echo "Proceeding..."
                account_regex="^[0-9]{12}$"
                while ! [[ $account =~ $account_regex ]]
                do
                    read -p "Enter the AWS Account ID that will be used to publish to Serverless Application Repository (limits SAR access to applications on the specified account: " account
                    if ! [[ $account =~ $account_regex ]];
                        then echo "Enter a valid AWS Account ID"
                    fi
                done
                cat > sar_bucket_policy.json <<- EOM
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service":  "serverlessrepo.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:$PARTITION:s3:::$1/*",
      "Condition": {
        "StringEquals": {
            "aws:SourceAccount": "$account"
        }
      }
    }
  ]
}
EOM
                cat sar_bucket_policy.json
                set -e
                aws s3api put-bucket-policy --bucket $1 --region $REGION --policy  file://sar_bucket_policy.json
                rm sar_bucket_policy.json
                break;;
            [Nn]* ) echo "Skipping bucket policy, not that this may result in failed attempts to publish to Serverless Application Repository"; break;;
            * ) echo "Please answer yes or no.";;
        esac
    done
fi

set -e
mvn clean install -Dpublishing=true

# 8. Packages the connector using SAM to create a deployable CloudFormation template
# 9. Publishes the connector using SAM