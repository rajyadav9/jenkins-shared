import hudson.model.Result
import hudson.model.Run
import jenkins.model.CauseOfInterruption.UserInterruption
def call(){
    currentDisplayName = env.GERRIT_PROJECT+' '+env.GERRIT_BRANCH+' '+env.GERRIT_CHANGE_NUMBER
    Run previousBuild = currentBuild.rawBuild.getPreviousBuildInProgress()

    while (previousBuild != null) {
        if (previousBuild.isInProgress()) {
            echo ">>DisplayName of previous Build : ${previousBuild.displayName}"
            if(previousBuild.displayName.find(currentDisplayName)) {
                def executor = previousBuild.getExecutor()
                if (executor != null) {
                    echo ">> Aborting older build #${previousBuild.number}"
                    executor.interrupt(Result.ABORTED, new UserInterruption(
                            "Aborted by newer build #${currentBuild.number}"
                    ))
                }
            }
        }
        previousBuild = previousBuild.getPreviousBuildInProgress()
    }
}