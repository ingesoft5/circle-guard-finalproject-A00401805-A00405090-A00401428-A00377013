#!/usr/bin/env groovy
// CG-DOC: standalone evidence runner for ciServices selective execution.
// No Jenkins required: stubs env/sh/echo/error and loads jenkins/vars/ciServices.groovy.
// Usage (from repo root, using the Groovy jars bundled with one Gradle distribution):
//   LIB=$(find ~/.gradle/wrapper/dists -type d -path '*/gradle-8.14.5/lib' | head -n 1)
//   CP=$(find "$LIB" -maxdepth 1 -name 'groovy-*.jar' | tr '\n' ':')
//   java -cp "$CP" groovy.ui.GroovyMain jenkins/test/TestCiServices.groovy
// Exit code 0 = all cases pass; 1 = at least one failure.

def varPath = args ? args[0] : 'jenkins/vars/ciServices.groovy'
def currentDiff = ''
def binding = new Binding()
binding.setVariable('env', [CHANGE_TARGET: 'main', BRANCH_NAME: 'PR-TEST'])
binding.setVariable('echo', { String msg -> null })
binding.setVariable('error', { String msg -> throw new IllegalStateException(msg) })
binding.setVariable('sh', { args ->
    // changed() calls sh(String) for git fetch and sh(Map) for git diff.
    if (args instanceof Map) return currentDiff
    return null
})

def script = new GroovyShell(binding).parse(new File(varPath))
def ALL = ['gateway', 'auth', 'form', 'identity', 'promotion', 'notification', 'file', 'dashboard']
int failures = 0

println '== verifySelection() =='
try {
    script.verifySelection()
    println 'PASS  built-in invariants'
} catch (Throwable ex) {
    println "FAIL  built-in invariants: ${ex.message}"
    failures++
}

println '== selectFromFiles() =='
def pureCases = [
        ['promotion only', ['services/circleguard-promotion-service/src/main/java/P.java'], ['promotion']],
        ['promotion + form', ['services/circleguard-promotion-service/src/main/java/P.java', 'services/circleguard-form-service/src/main/java/F.java'], ['form', 'promotion']],
        ['promotion + service deploy manifest', ['services/circleguard-promotion-service/src/main/java/P.java', 'deploy/k8s/base/promotion-alerts.yml'], ['promotion']],
        ['form deploy manifest', ['deploy/k8s/base/form-storage.yml'], ['form']],
        ['mobile and docs', ['mobile/App.tsx', 'docs/informe/HU-FORM-06.md'], []],
        ['docs plus form', ['docs/informe/HU-FORM-06.md', 'services/circleguard-form-service/src/main/java/F.java'], ['form']],
        ['CI-only: Jenkinsfile', ['Jenkinsfile'], []],
        ['CI-only: shared library', ['jenkins/vars/ci.groovy'], []],
        ['CI-only plus promotion', ['Jenkinsfile', 'services/circleguard-promotion-service/src/main/java/P.java'], ['promotion']],
        ['global Gradle file', ['build.gradle.kts'], ALL],
        ['shared deploy config', ['deploy/k8s/base/apps.yml'], ALL]
]
pureCases.each { c ->
    def actual = script.selectFromFiles(c[1])*.short
    def ok = actual == c[2]
    println "${ok ? 'PASS' : 'FAIL'}  ${c[0]}  ->  ${actual}"
    if (!ok) failures++
}

println '== changed() with stubbed git diff =='
def changedCases = [
        ['service diff', 'services/circleguard-promotion-service/README.md\n', ['promotion']],
        ['Jenkinsfile diff', 'Jenkinsfile\n', []],
        ['shared library diff', 'jenkins/vars/ci.groovy\n', []],
        ['CI plus service diff', 'Jenkinsfile\nservices/circleguard-promotion-service/README.md\n', ['promotion']],
        ['global Gradle diff', 'build.gradle.kts\n', ALL],
        ['mobile diff', 'mobile/App.tsx\n', []]
]
changedCases.each { c ->
    currentDiff = c[1]
    def actual = script.changed()*.short
    def ok = actual == c[2]
    println "${ok ? 'PASS' : 'FAIL'}  ${c[0]}  ->  ${actual}"
    if (!ok) failures++
}

println failures == 0 ? 'ALL CASES PASSED' : "${failures} CASE(S) FAILED"
System.exit(failures == 0 ? 0 : 1)
