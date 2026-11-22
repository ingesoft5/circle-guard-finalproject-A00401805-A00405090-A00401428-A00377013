#!/usr/bin/env groovy
// CG-DOC: standalone evidence runner for ciServices.changed() scope rules.
// No Jenkins required: stubs env/sh/echo and loads jenkins/vars/ciServices.groovy.
// Usage (from repo root): groovy jenkins/test/TestChanged.groovy [path/to/ciServices.groovy]
// Exit code 0 = all cases pass; 1 = at least one failure.

def varPath = args ? args[0] : 'jenkins/vars/ciServices.groovy'
def binding = new Binding()
binding.setVariable('env', new Expando(CHANGE_TARGET: 'main', BRANCH_NAME: 'PR-TEST'))
def shell = new GroovyShell(binding)
def script = shell.parse(new File(varPath))

def currentDiff = ''
script.metaClass.sh = { String cmd -> null }        // git fetch stub
script.metaClass.sh = { Map args -> currentDiff }    // git diff --name-only stub
script.metaClass.echo = { String msg -> null }

def ALL = ['gateway', 'auth', 'form', 'identity', 'promotion', 'notification', 'file', 'dashboard'].toList()
def cases = [
        [
                name: 'ci-safe: controller Dockerfile only',
                diff: 'deploy/compose/tools/jenkins/Dockerfile\n',
                expected: []
        ],
        [
                name: 'ci-safe: plugins.txt only',
                diff: 'deploy/compose/tools/jenkins/plugins.txt\n',
                expected: []
        ],
        [
                name: 'ci-safe: smee-client only',
                diff: 'deploy/compose/tools/smee-client/run.sh\n',
                expected: []
        ],
        [
                name: 'ci-affecting: tools compose stays global (TESTCONTAINERS_*)',
                diff: 'deploy/compose/tools/docker-compose.onpremise.yml\n',
                expected: ALL
        ],
        [
                name: 'ci-affecting: jenkins/cac stays global (executors, credentials)',
                diff: 'deploy/compose/tools/jenkins/cac/jenkins.yml\n',
                expected: ALL
        ],
        [
                name: 'k8s manifest stays global',
                diff: 'deploy/k8s/foo.yaml\n',
                expected: ALL
        ],
        [
                name: 'service path selects exactly one service',
                diff: 'services/circleguard-form-service/src/main/java/A.java\n',
                expected: ['form']
        ],
        [
                name: 'mixed: ci-safe file plus one service does not go global',
                diff: 'deploy/compose/tools/jenkins/plugins.txt\nservices/circleguard-promotion-service/README.md\n',
                expected: ['promotion']
        ]
]

int failures = 0
cases.each { c ->
    currentDiff = c.diff
    def result = script.changed()*.short
    def ok = (result == c.expected)
    println "${ok ? 'PASS' : 'FAIL'}  ${c.name}  ->  ${result}"
    if (!ok) failures++
}
println failures == 0 ? "ALL ${cases.size()} CASES PASSED" : "${failures}/${cases.size()} CASES FAILED"
System.exit(failures == 0 ? 0 : 1)
