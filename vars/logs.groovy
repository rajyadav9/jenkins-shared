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
        break
      case 'warning':
        log_warning config.message
        break
      case 'error':
        log_error config.message
        break
      default:
        error "Unhandled type."
    }
}

