def info(message) {
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

