import com.atlassian.jira.component.ComponentAccessor
import static com.atlassian.jira.issue.IssueFieldConstants.ISSUE_TYPE

def allIssueTypes = ComponentAccessor.constantsManager.allIssueTypeObjects
def issueTypeField = getFieldById(ISSUE_TYPE)
def availableIssueTypes = []

// Enter issue types to be hidden here
def hiddenIssueTypes = ["Improvement", "Request", "Miscellaneous", "Emergency"]

availableIssueTypes.addAll(allIssueTypes.findAll { 
    def hiddenIssueType = it.name in hiddenIssueTypes
    if (!hiddenIssueType)
		availableIssueTypes.addAll(it.name)
})

issueTypeField.setFieldOptions(availableIssueTypes)
