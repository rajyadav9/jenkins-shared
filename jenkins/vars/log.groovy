cddef info(message) {
    echo "INFO: ${message}"
}

def warning(message) {
    echo "WARNING: ${message}"
}
def log_info(message) {
    echo "INFO: ${message}"
}

def log_warning(message) {
    echo "WARNING: ${message}"
}

def log_error(message) {
    echo "ERROR: ${message}"
}

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    switch (config.type) {
      case 'info':
        log_info config.message
	echo "infooo"
        break
      case 'warning':
        log_warning config.message
	echo "warn"
        break
      case 'error':
        log_error config.message
        break
      default:
        error "Unhandled type."
    }
}














//                         env.REGION = "us-west-2"
//                         env.PROJECT_NAME = 'admin-core-web'
//                         env.SERVICE_PATH = ""
//                         env.ECS_SERVICE_NAME = "hrx-admin-core-web-service"
//                         env.SSM_PARAM_PREFIX = "hrx-core-web"
//                         env.aaa = "my name is raj"
//                         echo "racnckcknckncksnc"
//
//                         env.build_disp=currentBuild.rawBuild.getPreviousBuildInProgress()
//                         echo "buildaaaaaa"
//                         echo "${env.build_disp}"
//                     }
//                 }
//             }
// stage ('env') {
//             steps {
//             script{
//             env.XYZ= "ABCABCABC"
//                                 zzz()
//
//             }
//     }
//     }
//         stage ('Example') {
//             steps {
//                 // log.info 'Starting'
//                  abc(
//                  REGION: "${REGION}",
//                  PROJECT_NAME: "${PROJECT_NAME}",
//                  )
//                  echo "jai shree ram"
//                 echo "${env.XYZ}"
//
//             }
//     }
// //     stage('Set variables')
// //                 {
// //                     steps {
// //                             aws1 = abc(
// //                                                    REGION: "${REGION}",
// //                                                    PROJECT_NAME: "${PROJECT_NAME}",
// //                                                    ).aws
// // //                          zzz(
// // //                                 aws: "${aws1}"
// // //                             )
// //
// //                     }
// //                // }
// }
//