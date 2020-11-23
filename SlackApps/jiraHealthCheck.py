import os
import json
import urllib3
from datetime import datetime

api_url = 'https://jira.rallyhealth.com/rest/troubleshooting/1.0/check'
webhook_url = os.environ.get('SLACK_WEBHOOK_URL')
myToken = os.environ.get('JIRA_API_KEY')
excludedChecks = ['com.atlassian.troubleshooting.plugin-jira:applinksStatusHealthCheck']

def getHeader():
    return {
        'Content-Type': 'application/json', 'user-agent': 'jiraHealthCheck/0.1',
        'Authorization': 'Basic {0}'.format(myToken)
    }

def makeCall():
    headers = getHeader()
    http = urllib3.PoolManager()
    response = http.request('GET', api_url, headers=headers)

    if (response.status_code == 201 or response.status_code == 200 or response.status_code == 422):
        return json.loads(response.content.decode('utf-8'))
    else:
        print(response.content)
        return None
    
def lambda_handler(event, context):
    response = makeCall()
    if response is not None:
        for status in response['statuses']:
            if (status['isHealthy'] == False and status['completeKey'] not in excludedChecks):
                error_timedate = datetime.fromtimestamp(status['time']/1000).strftime("%A, %B %d, %Y %I:%M:%S")
                slack_text = '*' + status['name'] + ' Health Check Failed*\n*Reason:* ' + status['failureReason'] + '\n*Severity:* ' + status['severity']
                slack_data = {'text': slack_text}
                slack_response = http.request(
                    'POST', webhook_url, data=json.dumps(slack_data),
                    headers={'Content-Type': 'application/json'}
                )
    else:
        slack_text = '*REST API Health Check returned HTTP error status*'
        slack_data = {'text': slack_text}
        slack_response = http.request(
            'POST', webhook_url, data=json.dumps(slack_data),
            headers={'Content-Type': 'application/json'}
        )
    return {
        'statusCode': 200,
        'body': json.dumps('Hello from Lambda!')
    }




