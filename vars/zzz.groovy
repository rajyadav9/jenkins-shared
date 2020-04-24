import hudson.model.Result
import hudson.model.Run
import jenkins.model.CauseOfInterruption.UserInterruption
def call() {
    def hi = Hudson.instance
    def pname = env.JOB_NAME.split('/')[0]

    hi.getItem(pname).getItem(env.JOB_BASE_NAME).getBuilds().each{ build ->
        def exec = build.getExecutor()

        if (build.number != currentBuild.number && exec != null) {
            exec.interrupt(
                    Result.ABORTED,
                    new CauseOfInterruption.UserInterruption(
                            "Aborted by #${currentBuild.number}"
                    )
            )
            println("Aborted previous running build #${build.number}")
        } else {
            println("Build is not running or is current build, not aborting - #${build.number}")
        }
    }

}











//    currentDisplayName = env.GERRIT_PROJECT+' '+env.GERRIT_BRANCH+' '+env.GERRIT_CHANGE_NUMBER
//    Run previousBuild = currentBuild.rawBuild.getPreviousBuildInProgress()
//
//    while (previousBuild != null) {
//        if (previousBuild.isInProgress()) {
//            echo ">>DisplayName of previous Build : ${previousBuild.displayName}"
//            if(previousBuild.displayName.find(currentDisplayName)) {
//                def executor = previousBuild.getExecutor()
//                if (executor != null) {
//                    echo ">> Aborting older build #${previousBuild.number}"
//                    executor.interrupt(Result.ABORTED, new UserInterruption(
//                            "Aborted by newer build #${currentBuild.number}"
//                    ))
//                }
//            }
//        }
//        previousBuild = previousBuild.getPreviousBuildInProgress()
//    }
