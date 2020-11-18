import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter

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
    String transition
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
    	epic.transition = "In Progress"
    else if (!isOpen) 
        epic.transition = "Close"
    else 
        epic.transition = "Open"
    
    return epic
}

void checkLastUpdated(Timestamp lastUpdated, List<Epic> epics, Epic epic) {
    if (!lastUpdated || (epic.lastUpdated > lastUpdated)) {
        epics << epic
    }
}

@BaseScript CustomEndpointDelegate delegate
getEpicRollups(httpMethod: "GET", groups: ["users"]) { MultivaluedMap queryParams, String body, HttpServletRequest request ->
    List<Epic> epics = []
    
    Timestamp lastUpdated
    
    if (queryParams.getFirst("lastUpdated")) {
        lastUpdated = Timestamp.valueOf(queryParams.getFirst("lastUpdated").toString())
    }
       
    if (queryParams.getFirst("key")) {
        Epic epic = getStoryPointsForEpic(queryParams.getFirst("key").toString())      
        checkLastUpdated(lastUpdated, epics, epic)
        
    } else {       
        def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
        def searchService = ComponentAccessor.getComponent(SearchService.class)
        def issueManager = ComponentAccessor.getIssueManager()
        def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
        
        def epicsquerystring = "issuetype = Epic and 'Is CA PPM Task' = Yes"
        def epicsquery = jqlQueryParser.parseQuery(epicsquerystring)
        def epicsresults = searchService.search(user, epicsquery, PagerFilter.getUnlimitedFilter())
        
        epicsresults.getResults().each { epicIssue ->
            Epic epic = getStoryPointsForEpic(epicIssue.getKey())
            
            def epicLastUpdated = epicIssue.getUpdated()
            if (!epic.lastUpdated || epicLastUpdated > epic.lastUpdated)
            {
                epic.lastUpdated = epicLastUpdated
            }
            checkLastUpdated(lastUpdated, epics, epic)
        } 
    }
    return Response.ok(JsonOutput.toJson(epics)).build()
}
