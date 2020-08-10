@Library('vab') _

pipeline {
  agent any

  stages {
    stage("Initialize") {
      steps {
        echo "Initialize"
      }
    }

    stage("Changes Detected") {
      stages {
        stage("Build") {
          steps {
            echo 'Build App'
          }
        }

        stage("Test") {
          parallel {
            stage("Static Code Analysis") {
              steps {
                echo "Run static code analysis on DemoApp"
                sleep 2
              }
            }

            stage("Unit Tests") {
              steps {
                echo "Run unit tests on DemoApp"
                sleep 5
              }
            }

            stage("Integration Tests") {
              steps {
                echo "Run integration tests on DemoApp"
                sleep 3
              }
            }

            stage("E2E Test") {
              steps {
                echo "Run E2E tests on DemoApp"
                CoolFunctionality()
              }
            }
          }
        }

        stage("Package") {
          when {
            branch 'master'
          }
          steps {
            echo "Package DemoApp and ship to payload bay."
            sleep 2
          }
        }
      }
    }

    stage("Finalize") {
      steps {
        echo "The final step of the pipeline (not in a conditional)."
      }
    }
  }
}
