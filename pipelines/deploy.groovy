pipeline {
  agent any

  options {
        timeout(time: 10, unit: 'MINUTES')
  }

  parameters {
    choice(name: 'TargetEnvironment', choices: ['Dev', 'QA', 'Staging', 'Production'], description: 'Where to deploy?')
  }

  stages {
    stage("Initialize") {
      steps {
        echo "initalize pieline"
        sleep 2
      }
    }

    stage("Deploy Gate") {
    when {
      expression { params.TargetEnvironment == 'Production' }
    }
    steps {
      echo "Run manual tests against Production."
      input message: "Are you sure you want to deploy to ${params.TargetEnvironment}?", ok: "YES!"
    }
  }
    stage("Deploy") {
      steps {
        echo "Deploying ${applicationName} to ${params.TargetEnvironment}!"
        echo "Start ${applicationName} in ${params.TargetEnvironment} and wait for health checks to pass."
        sleep 3
      }
    }

    stage("Testing") {
      parallel {
        stage("Automated Testing") {
          steps {
            echo "Run automated tests (like selenium) against ${params.TargetEnvironment}"
            sleep 2
          }
        }
        stage("Manual Testing") {
          steps {
            echo "Run manual tests against ${params.TargetEnvironment}"
            input message: "Are manual tests completed?", ok: "Done!"
          }
          options {
            timeout(time: 2, unit: 'MINUTES')
          }
        }
      }
    }

    stage("Finishing Touches") {
      when {
        equals expected: 'Production', actual: params.TargetEnvironment
      }
      stages {
        stage("Tag Repo") {
          steps {
            echo "tagging repo"
            sleep 2
          }
        }

        stage("Generate release notes") {
          steps {
            echo "generating release notes"
            sleep 5
          }
        }

        stage("Release Notification") {
          steps {
            echo "internal notification about release (slack post?)"
            sleep 3
          }
        }
      }
    }

    stage("Finalize") {
      steps {
        echo "the finishing line"
        sleep 2
      }
    }
  }
}

// Anything below here would not be in the pipeline but would be provided by a shared library that will be provided through Artifactory
String getApplicationName() {
  return (JOB_NAME - "/${JOB_BASE_NAME}")
}