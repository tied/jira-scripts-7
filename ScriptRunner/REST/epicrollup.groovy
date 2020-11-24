import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.workflow.TransitionOptions

import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import groovy.json.JsonOutput
import groovy.transform.BaseScript

import javax.servlet.http.HttpServletRequest

import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response
import java.sql.Timestamp

class Epic {
    String key
    int ptsTotal = 0
    int ptsAccepted = 0
    int ptsExcluded = 0
    String status
    Timestamp lastUpdated
}

Epic getStoryPointsForEpic(String key) {
    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchService = ComponentAccessor.getComponent(SearchService.class)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    def storyquerystring, storyquery, storyresults
    int ptsStory
    boolean isOpen = false
    boolean isInProgress = false
    boolean isClosed = false
    Object storyPointsObj
    
    Epic epic = new Epic()
    epic.key = key
    storyquerystring = "issueFunction in issuesInEpics('key = " + key + "') ORDER BY updated ASC"
    storyquery = jqlQueryParser.parseQuery(storyquerystring)
    storyresults = searchService.search(user, storyquery, PagerFilter.getUnlimitedFilter())
    storyresults.getResults().each { storyIssue ->
        storyPointsObj = storyIssue.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10013))
        if (storyPointsObj instanceof Number) {
            ptsStory = (Integer) storyPointsObj
        } else {
            ptsStory = 0
        }
         
        switch (storyIssue.getStatus().name) {
            case "Closed": 
                epic.ptsAccepted = epic.ptsAccepted + ptsStory
                isClosed = true
                break
            case "Open": 
                isOpen = true
                break
            default:
                isInProgress = true
        }
            
        if (storyIssue.getResolution()) {
            switch (storyIssue.getResolution().name) {
                case "Won't Do":
                case "Won't Fix":
                case "Duplicate":
                case "Incomplete":
                case "Cannot Reproduce":
                case "Under Advisement":
                case "Partner Issue": 
                    epic.ptsExcluded = epic.ptsExcluded + ptsStory
            }
        }
            
        epic.ptsTotal = epic.ptsTotal + ptsStory
        epic.lastUpdated = storyIssue.getUpdated()
    }
    
    if (isInProgress || (isOpen && isClosed))
    	epic.status = "In Progress"
    else if (!isOpen) 
        epic.status = "Closed"
    else 
        epic.status = "Open"
    
    return epic
}

@BaseScript CustomEndpointDelegate delegate
getEpicRollups(httpMethod: "GET", groups: ["users"]) { MultivaluedMap queryParams, String body, HttpServletRequest request ->
    List<Epic> epics = []

    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchService = ComponentAccessor.getComponent(SearchService.class)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    Timestamp lastUpdated
    def epicsquerystring
    
    if (queryParams.getFirst("lastUpdated")) {
        lastUpdated = Timestamp.valueOf(queryParams.getFirst("lastUpdated").toString())
    }
       
    if (queryParams.getFirst("key"))
        epicsquerystring = "issuetype = Epic and key = " + queryParams.getFirst("key").toString()
    else    
        epicsquerystring = "issuetype = Epic and 'Is CA PPM Task' = Yes"
        
    def epicsquery = jqlQueryParser.parseQuery(epicsquerystring)
    def epicsresults = searchService.search(user, epicsquery, PagerFilter.getUnlimitedFilter())
        
    epicsresults.getResults().each { epicIssue ->
        Epic epic = getStoryPointsForEpic(epicIssue.getKey())
        
        def epicLastUpdated = epicIssue.getUpdated()
        if (!epic.lastUpdated || epicLastUpdated > epic.lastUpdated)
            epic.lastUpdated = epicLastUpdated
        
        if (!lastUpdated || (epic.lastUpdated > lastUpdated)) {
            def epicStatus = epicIssue.getStatus().name
            if (1 == 0 && (epicStatus != epic.status && epicStatus != "Launched")) {
            	def workflow = ComponentAccessor.workflowManager.getWorkflow(epicIssue)
            	def status = epic.status
            	if (epic.status ==  "Closed")
            		status = "Close"
				def actionId = workflow.allActions.findByName(status)?.id
    			def issueLinkManager = ComponentAccessor.issueLinkManager
            	def issueService = ComponentAccessor.issueService
				def issueInputParameters = issueService.newIssueInputParameters()

				issueInputParameters.setComment('This Epic transitioned by getEpicsRollup service for Clarity integration based on child issue statuses')
				issueInputParameters.setSkipScreenCheck(true)
            
            	def transitionOptions = new TransitionOptions.Builder()
    				.skipConditions()
    				.skipPermissions()
    				.skipValidators()
    				.build()
            
            	def transitionValidationResult = issueService.validateTransition(user, epicIssue.id, actionId, issueInputParameters, transitionOptions)
            	issueService.transition(user, transitionValidationResult)
            }
            epics << epic
        }
    }
    return Response.ok(JsonOutput.toJson(epics)).build()
}
