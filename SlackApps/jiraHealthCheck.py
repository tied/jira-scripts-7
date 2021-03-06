import os
import json
import urllib3

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

    if (response.status == 201 or response.status == 200 or response.status == 422):
        print('#JIRA_HEALTH_CHECK_SUCCESS')
        return json.loads(response.data.decode('utf-8'))
    else:
        print('#JIRA_HEALTH_CHECK_FAILURE')
        print(response.status)
        return None
    
def lambda_handler(event, context):
    response = makeCall()
    http = urllib3.PoolManager()
    if response is not None:
        for status in response['statuses']:
            print(status['completeKey'] + ' >> ' + str(status['isHealthy']))
            if (status['isHealthy'] == False and status['completeKey'] not in excludedChecks):
                slack_text = '*' + status['name'] + ' Health Check Failed*\n*Reason:* ' + status['failureReason'] + '\n*Severity:* ' + status['severity']
                slack_data = {'text': slack_text}
                slack_response = http.request(
                    'POST', webhook_url, body=json.dumps(slack_data),
                    headers={'Content-Type': 'application/json'}
                )
                
    else:
        slack_text = '*REST API Health Check returned HTTP error status*'
        slack_data = {'text': slack_text}
        slack_response = http.request(
            'POST', webhook_url, body=json.dumps(slack_data),
            headers={'Content-Type': 'application/json'}
        )
