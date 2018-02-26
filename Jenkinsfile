#!/usr/bin/env groovy

node {
    properties([
        gitLabConnection('jenkins'),
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
        parameters([
            choice(choices: '\nhttps://dtr.jbs.com.ua\nhttps://index.docker.io/v1/', description: '', name: 'dockerhub_url'), 
            credentials(credentialType: 'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl', defaultValue: '', description: '', name: 'dockerhub_auth', required: true)
        ])
    ])

    stage('check params'){
        params.each{
            if(it.value.matches('')){
                error "Param "+it.key+" is empty"
            }
        }
    }

    stage('checkout') {
        checkout scm
    }

    stage('backend tests') {
        gitlabCommitStatus {
            try {
                sh "chmod +x gradlew"
                sh "./gradlew -PnodeInstall --no-daemon --refresh-dependencies clean check test"
            } catch(err) {
                throw err
            } finally {
                junit '**/build/**/TEST-*.xml'
                /**
                 * ICT^hothouse required automatic static analysis
                 */
                step([$class: 'CheckStylePublisher',
                      changeBuildStatus: true,
                      pattern: '**/build/reports/checkstyle/**xml',
                      usePreviousBuildAsReference: true])
                step([$class: 'FindBugsPublisher',
                      changeBuildStatus: true,
                      pattern: '**/build/reports/findbugs/**xml',
                      usePreviousBuildAsReference: true])
                step([$class: 'PmdPublisher',
                      changeBuildStatus: true,
                      pattern: '**/build/reports/pmd/**xml',
                      usePreviousBuildAsReference: true])
                step([$class: 'JacocoPublisher',
                      exclusionPattern: '**/*Test*,**/*Enhancer*,**/FastClass*,**/*Builder,**/example/**,**/test/**',
                      changeBuildStatus: true,
                      minimumClassCoverage: '80',
                      minimumLineCoverage: '80',
                      minimumMethodCoverage: '80'])
            }
        }
    }

    stage('packaging') {
        gitlabCommitStatus {
            sh "./gradlew -x test -Pprod -PnodeInstall --no-daemon bootRepackage"
            archiveArtifacts artifacts: '**/build/libs/*.war', fingerprint: true
        }
    }

    stage('quality analysis') {
        gitlabCommitStatus {
            withSonarQubeEnv('sonar.jbs.com.ua') {
                sh "./gradlew -x test --no-daemon sonarqube"
            }
        }
    }

    def dockerImage
    def org
    stage('build docker') {
        gitlabCommitStatus {
            sh "cp -R src/main/docker build/"
            sh "cp build/libs/*.war build/docker/"
            if (params.dockerhub_url.matches('https://dtr.jbs.com.ua')) {
                org = "icthothouse"
            } else {
                org = "xmonline"
            }
            dockerImage = docker.build(org+'/xm-ms-config', 'build/docker')
        }
    }

    stage('push docker') {
        gitlabCommitStatus {
            docker.withRegistry("${params.dockerhub_url}", "${params.dockerhub_auth}") {
                dockerImage.push('latest')
            }
        }
    }

    stage('clear') {
        sh "docker rmi -f ${dockerImage.id}"
    }

    gitlabCommitStatus {
    }

}
