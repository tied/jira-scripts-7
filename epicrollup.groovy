import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter

import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import groovy.json.JsonBuilder
import groovy.transform.BaseScript

import javax.servlet.http.HttpServletRequest

import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response
import java.sql.Timestamp

class Epic {
    String key
	double ptsTotal = 0
	double ptsAccepted = 0
	double ptsExcluded = 0
	Timestamp lastUpdated
}

@BaseScript CustomEndpointDelegate delegate
getEpicRollups { queryParams, body, HttpServletRequest request ->
	def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
	def searchService = ComponentAccessor.getComponent(SearchService.class)
	def issueManager = ComponentAccessor.getIssueManager()
	def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    
    Epic[] epics

	String epicsquerystring = "issue type = Epic and customfield_10013 = Yes"
	def epicsquery = jqlQueryParser.parseQuery(epicsquerystring)
	def epicsresults = searchService.search(user, epicsquery, PagerFilter.getUnlimitedFilter())

	Object storyPointsObj
	def storyquerystring, storyquery, storyresults
    double ptsStory

	epicsresults.getResults().each {epicIssue ->
	    Epic epic = new Epic()

	    storyquerystring = "issueFunction in issuesInEpics('key = " + epicIssue.key + "') ORDER BY updated ASC"
	    storyquery = jqlQueryParser.parseQuery(storyquerystring)
	    storyresults = searchService.search(user, storyquery, PagerFilter.getUnlimitedFilter())
 	    epic.key = epicIssue.getKey()
    
 	    storyresults.getResults().each {storyIssue ->
        	storyPointsObj = storyIssue.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObject(19430))
        	if (storyPointsObj instanceof Number) {
 		       	ptsStory = ((Number) storyPointsObj).doubleValue();
        	} else {
            	ptsStory = 0
        	}
        
	    	if (storyIssue.getStatusId() == "Closed") {
	        	epic.ptsAccepted = epic.ptsAccepted + ptsStory
	   		}
    
			switch (storyIssue.getResolutionId()){
	    		case "Won't Do":
	    		case "Won't Fix":
        		case "Duplicate":
        		case "Incomplete":
        		case "Cannot Reproduce":
        		case "Under Advisement":
        		case "Partner Issue": 
         	   		epic.ptsExcluded = epic.ptsExcluded + ptsStory
			}
            
        	epic.ptsTotal = epic.ptsTotal + ptsStory
        	epic.lastUpdated = storyIssue.getUpdated()
    	}
    
    	epics.plus(epic)
	}
    
    JsonBuilder builder = new JsonBuilder()
    
    builder {
        epics epics.collect {
            [
                Key: it.key(),
                PointsTotal: it.ptsTotal(),
                PointsAccepted: it.ptsAccepted(),
                PointsExcluded: it.ptsExcluded(),
                LastUpdated: it.lastUpdated()
            ]
        }
    }
    
	return Response.ok(builder.toString()).build()
}