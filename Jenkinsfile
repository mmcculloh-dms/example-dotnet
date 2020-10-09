@Library('vab') _

pipeline {
    agent {
        kubernetes { // Agent yaml will be set at a global level
            yaml """
            apiVersion: v1
            kind: Pod
            spec:
                containers:
                    - name: node12
                      image: timbru31/java-node
                      tty: true
                      command:
                        - cat
            """
        }
    }
    environment {
        // Most (if not all) of these variables should be able to be set automatically through Ignition
        CI = 'true'
        DEFAULT_CONTAINER = "node12"
    }
    stages {
        stage('Blah') {
            steps {
              withSpaceport {
                sh 'printenv'
              }
            }
        }
    }
}
