pipeline {
  agent any

  options {
        timeout(time: 10, unit: 'MINUTES')
  }

  environment {
    DOT_NET_CORE_APP_NAME = "appOne"
    DOT_NET_CORE_BUILD_CONFIGURATION = "Release"
    DOT_NET_CORE_APP_SRC = "./${DOT_NET_CORE_APP_NAME}/src/"
    DOT_NET_CORE_APP_SLN = "${DOT_NET_CORE_APP_SRC}${DOT_NET_CORE_APP_NAME}.sln"
    DOT_NET_CORE_BUILD_OUTPUT_FOLDER = "${DOT_NET_CORE_APP_SRC}${DOT_NET_CORE_APP_NAME}/bin/${DOT_NET_CORE_BUILD_CONFIGURATION}/netcoreapp3.1/"
    DOT_NET_CORE_APP_DLL =  "${DOT_NET_CORE_BUILD_OUTPUT_FOLDER}${DOT_NET_CORE_APP_NAME}.dll"
  }

  stages {
    stage("Initialize") {
      steps {
        initializeBuildPipeline()
      }
    }

    stage("Changes Detected") {
      when {
        equals expected: true , actual: env.CHANGE_DETECTED.toBoolean()
      }
      stages {
        stage("Build") {
          steps {
            sh "dotnet restore ${DOT_NET_CORE_APP_SLN}"
            sh "dotnet clean ${DOT_NET_CORE_APP_SLN}"
            sh "dotnet build ${DOT_NET_CORE_APP_SLN} --configuration ${DOT_NET_CORE_BUILD_CONFIGURATION}"
          }
        }

        stage("Test") {
          parallel {
            stage("Static Code Analysis") {
              steps {
                echo "Run static code analysis on ${applicationName}"
                sleep 2
              }
            }

            stage("Unit Tests") {
              steps {
                echo "Run unit tests on ${applicationName}"
                sleep 5
              }
            }

            stage("Integration Tests") {
              steps {
                echo "Run integration tests on ${applicationName}"
                sleep 3
              }
            }

            stage("Other Tests") {
              steps {
                sh "dotnet ${DOT_NET_CORE_APP_DLL}"
              }
            }
          }
        }

        stage("Package") {
          when {
            branch 'master'
          }
          steps {
            echo "Package ${applicationName} and ship to payload bay."
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

  post {
    always {
      finalizeBuildPipeline()
    }
  }
}

// Anything below here would not be in the pipeline but would be provided by a shared library that will be provided through Artifactory

Map<String,String> getUrlParts(String url) {
  def repoPattern = "^(https|git)(://|@)([^/:]+)[/:]([^/:]+)/(.+).git\$"
  def (_,protocol,seperator,hostname,ownerName,repositoryName) = (GIT_URL =~ repoPattern)[0]
  def results = [:]
  results.'full' = _
  results.'protocol' = protocol
  results.'seperator' = seperator
  results.'hostname' = hostname
  results.'ownerName' = ownerName
  results.'repositoryName' = repositoryName
  return results
}

String getRepoOwner() {
  return getUrlParts(GIT_URL).ownerName
}

String getRepoName() {
  return getUrlParts(GIT_URL).repositoryName
}

String getCurrentTime() {
  return new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
}

String getApplicationName() {
  return (JOB_NAME - "/${JOB_BASE_NAME}")
}

void getCurrentGitCommitHash() {
  return sh(script: "git rev-parse --verify HEAD", returnStdout: true).trim();
}

void createCheck() {
  withCredentials([usernamePassword(credentialsId: 'github-app-jenkinsrd',
                                    usernameVariable: 'GITHUB_APP',
                                    passwordVariable: 'GITHUB_JWT_TOKEN')]) {
    def output = sh(script: """
      curl --show-error --fail \
            -X POST \
            -H "Content-Type: application/json" \
            -H "Accept: application/vnd.github.antiope-preview+json" \
            -H "authorization: Bearer ${GITHUB_JWT_TOKEN}" \
            -d '{ "name": "${applicationName}", \
                  "head_sha": "'${GIT_COMMIT}'", \
                  "details_url": "${RUN_DISPLAY_URL}", \
                  "status": "in_progress", \
                  "external_id": "${BUILD_NUMBER}", \
                  "started_at": "${currentTime}", \
                  "output": { "title": "Pending check run from Mission Control...", \
                              "summary": "This is a check run which has been generated from Mission Control using a GitHub App"}}' \
            https://api.github.com/repos/${repoOwner}/${repoName}/check-runs
      """, returnStdout: true).trim()

    def json = readJSON text: output

    env.CHECK_RUN_ID=json.id;
  }
}

void concludeCheck() {
  status = currentBuild.resultIsBetterOrEqualTo("SUCCESS") ? (env.CHANGE_DETECTED.toBoolean() ? "success" : "neutral") : "failure";
  outputTitle = currentBuild.resultIsBetterOrEqualTo("SUCCESS") ? (env.CHANGE_DETECTED.toBoolean() ? "Passed" : "Skipped") : "Failed";
  outputTitle = outputTitle + " check run from Mission Control."
  outputSummary = currentBuild.resultIsBetterOrEqualTo("SUCCESS") ? (env.CHANGE_DETECTED.toBoolean() ? "" : "No watched files changed since last sucessful build.") : "";
  outputSummary = "This is a check run which has been generated from Mission Control using a GitHub App.<br /><br />" + outputSummary

  withCredentials([usernamePassword(credentialsId: 'github-app-jenkinsrd',
                                    usernameVariable: 'GITHUB_APP',
                                    passwordVariable: 'GITHUB_JWT_TOKEN')]) {
    sh """
      curl --show-error --fail \
            -X PATCH \
            -H "Content-Type: application/json" \
            -H "Accept: application/vnd.github.antiope-preview+json" \
            -H "authorization: Bearer ${GITHUB_JWT_TOKEN}" \
            -d '{ "conclusion": "${status}", \
                  "completed_at": "${currentTime}", \
                  "output": { "title": "${outputTitle}", \
                              "summary": "${outputSummary}"}}' \
            https://api.github.com/repos/${repoOwner}/${repoName}/check-runs/${CHECK_RUN_ID}
      """
  }
}

def isWatchedPath(def path) {
  return !!path &&
    path ==~ /${DOT_NET_CORE_APP_NAME}\/.*/ &&
    !(path ==~ /.*\.md/) &&
    !(path ==~ /.*\.gitignore/)

}

def commitHashForBuild(def build) {
  return build?.buildVariables?.GIT_COMMIT_HASH;
}

def generateGitDiffCompareParameters() {
  def currentBuildCommitHash = commitHashForBuild(currentBuild);
  def lastSuccessfulBuildCommitHash = commitHashForBuild(currentBuild.previousSuccessfulBuild);

  if(!!currentBuildCommitHash && !!lastSuccessfulBuildCommitHash) {
    return "${currentBuildCommitHash} ${lastSuccessfulBuildCommitHash}"
  }

  return null;
}

Boolean checkForChanges() {
  def gitDiffCompareParameters = generateGitDiffCompareParameters();

  if(!gitDiffCompareParameters) return true; // if we couldn't figure out what to compare, we'll assume there are changes
  return sh(
      script: "git diff ${gitDiffCompareParameters} --name-only",
      returnStdout: true
    ).split('\n')
      .collect({it.trim()})
      .any({isWatchedPath(it)});
}

void initializeBuildPipeline() {
  env.GIT_COMMIT_HASH = getCurrentGitCommitHash();
  env.CHANGE_DETECTED = checkForChanges();
  createCheck();
}

void finalizeBuildPipeline() {
  concludeCheck();
}
