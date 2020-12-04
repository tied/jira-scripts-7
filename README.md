# jira-scripts

A collection of scripts used on Rally's Jira instance.

## [Clarity Jira Integration](ScriptRunner/REST/epicrollup.groovy)

A custom REST API that calculates the total story points associated with an Epic. If no parameters are specified, the service returns rollups for all Epics associated with a Clarity PPM task. The following query parameters are evaluated by the service:

| Parameter | Description |
| --------- | ----------- |
| lastUpdated | Only returns Epics updated after specified date |
| key | Returns a single Epic with the specified key |

In addition to the service, the Clarity integration requires the following custom fields to be created and displayed on the Epic screen
| Field Name | Type | Description |
| --- | --- | --- |
| Is CA PPM Task | Checkbox | If checked, Epic is linked to a Clarity task |
| Clarity ID | Single Text | ID of Clarity PPM project linked to Epic |
| Clarity Link | URL | Link to Clarity PPM project |

Script Runner behaviors are implemented to prevent the custom fields from being updated in Jira. The Clarity integration updates these fields using the Jira REST API.

## [ScriptRunner Behaviors](ScriptRunner/behaviors)

Initializer scripts for ScriptRunner behaviors
| Script | Purpose |
| --- | --- |
| hideIssueTypes.groovy | Hides issue types no longer needed for a project as archiving issue types not available in Jira |

## [Jira Health Check](SlackApps/jiraHealthCheck.py)

A python script that calls the Jira Health Check API and if any errors are identified, posts a message to Slack. Deployable to AWS Lambda.

The application link health check is excluded as it always returns an error.
