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
    Timestamp lastUpdated
}

@BaseScript CustomEndpointDelegate delegate
getEpicRollups(httpMethod: "GET", groups: ["users"]) { MultivaluedMap queryParams, String body, HttpServletRequest request ->
    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchService = ComponentAccessor.getComponent(SearchService.class)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    
    def epics = []

    String epicsquerystring = "issuetype = Epic and 'Is CA PPM Task' = Yes"
    def epicsquery = jqlQueryParser.parseQuery(epicsquerystring)
    def epicsresults = searchService.search(user, epicsquery, PagerFilter.getUnlimitedFilter())

    Object storyPointsObj
    def storyquerystring, storyquery, storyresults
    int ptsStory
    
    Timestamp lastUpdated
    
    if (queryParams.getFirst("lastUpdated")) {
        lastUpdated = Timestamp.valueOf(queryParams.getFirst("lastUpdated").toString())
    }
    
    epicsresults.getResults().each {epicIssue ->
        Epic epic = new Epic()

        storyquerystring = "issueFunction in issuesInEpics('key = " + epicIssue.key + "') ORDER BY updated ASC"
        storyquery = jqlQueryParser.parseQuery(storyquerystring)
        storyresults = searchService.search(user, storyquery, PagerFilter.getUnlimitedFilter())
        epic.key = epicIssue.getKey()
    
        storyresults.getResults().each {storyIssue ->
            storyPointsObj = storyIssue.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObject(10013))
            if (storyPointsObj instanceof Number) {
                ptsStory = (Integer) storyPointsObj
            } else {
                ptsStory = 0
            }
        
            if (storyIssue.getStatus().name == "Closed") {
                epic.ptsAccepted = epic.ptsAccepted + ptsStory
            }
            
            if (storyIssue.getResolution()){
                switch (storyIssue.getResolution().name){
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
        
        if (!lastUpdated || (epic.lastUpdated > lastUpdated)) {
            epics << epic
        }
    } 
    return Response.ok(JsonOutput.toJson(epics)).build()
}
