import org.junit.Test

import static org.junit.Assert.*

/** Executes the root Jenkinsfile against local step doubles; no server or deployment is contacted. */
class JenkinsfileTest {
    @Test
    void automaticFormChangesUseTheSameFormOnlyWorkflow() {
        def run = new PipelineSimulation(target: 'auto', branch: 'PR-99',
                changedFiles: ['services/circleguard-form-service/src/main/java/Survey.java']).execute()
        assertNull(run.failure)
        assertEquals([':services:circleguard-form-service'], run.argumentsFor('buildAndTest'))
        assertTrue(run.argumentsFor('sonarScan').isEmpty())
        assertTrue(run.argumentsFor('deploy').isEmpty())
        assertTrue(run.commands.any { it.endsWith('circleguard-form-service:bootJar') })
    }

    @Test
    void formOnMainOnlyValidatesAndPackagesForm() {
        def run = new PipelineSimulation(target: 'form').execute()
        assertNull(run.failure)
        assertEquals([':services:circleguard-form-service'], run.argumentsFor('buildAndTest'))
        assertTrue(run.commands.any { it == './gradlew :services:circleguard-form-service:bootJar' })
        ['sonarScan', 'qualityGate', 'publish', 'dockerBuildPush', 'deploy', 'e2e'].each {
            assertTrue(it, run.argumentsFor(it).isEmpty())
        }
        assertFalse(run.commands.any { it.contains('git tag') || it.contains('git push') })
        assertFalse(run.executedStages.contains('Approval prod'))
    }

    @Test
    void gatewayOnMainKeepsItsReleaseAndProductionApproval() {
        def run = new PipelineSimulation(target: 'gateway').execute()
        assertNull(run.failure)
        assertEquals([':services:circleguard-gateway-service'], run.argumentsFor('buildAndTest'))
        assertEquals(['dev', 'stage', 'prod'], run.argumentsFor('deploy'))
        ['sonarScan', 'qualityGate', 'publish', 'dockerBuildPush', 'e2e'].each {
            assertEquals(it, 1, run.calls.count { call -> call.name == it })
        }
        assertEquals(1, run.approvals)
        assertTrue(run.commands.any { it.contains('git tag') })
    }

    @Test
    void aPrNeverPublishesOrDeploysEvenWhenGatewayIsSelected() {
        def run = new PipelineSimulation(target: 'gateway', branch: 'PR-99').execute()
        assertNull(run.failure)
        assertEquals(1, run.calls.count { it.name == 'sonarScan' })
        assertTrue(run.argumentsFor('publish').isEmpty())
        assertTrue(run.argumentsFor('dockerBuildPush').isEmpty())
        assertTrue(run.argumentsFor('deploy').isEmpty())
        assertEquals(0, run.approvals)
    }

    @Test
    void allRunsBothSequentiallyButOnlyGatewayHasReleaseOperations() {
        def run = new PipelineSimulation(target: 'all').execute()
        assertNull(run.failure)
        assertEquals([':services:circleguard-gateway-service', ':services:circleguard-form-service'],
                run.argumentsFor('buildAndTest'))
        assertEquals([':services:circleguard-gateway-service'], run.argumentsFor('publish'))
        assertEquals(2, run.commands.count { it.endsWith(':bootJar') })
        assertTrue(run.messages.any { it.contains('PENDIENTE: identity') })
    }

    @Test
    void automaticDocumentationChangesSkipBuildsAndReleases() {
        def run = new PipelineSimulation(target: 'auto', changedFiles: ['docs/infra/runbook.md']).execute()
        assertNull(run.failure)
        assertTrue(run.argumentsFor('buildAndTest').isEmpty())
        assertTrue(run.argumentsFor('deploy').isEmpty())
        assertTrue(run.commands.isEmpty())
    }

    @Test
    void failingTestsOrCoveragePreventPackagingAndReportFailure() {
        def run = new PipelineSimulation(target: 'form', failValidation: true).execute()
        assertNotNull(run.failure)
        assertFalse(run.commands.any { it.endsWith(':bootJar') })
        assertTrue(run.argumentsFor('deploy').isEmpty())
        assertEquals(['failure'], run.argumentsFor('notify'))
    }

    @Test
    void rollbackRemainsGatewayOnlyAndDoesNotBuild() {
        def run = new PipelineSimulation(target: 'form', rollbackTag: '1.2.3').execute()
        assertNull(run.failure)
        assertTrue(run.argumentsFor('buildAndTest').isEmpty())
        assertEquals(['prod'], run.argumentsFor('deploy'))
        assertEquals(['prod', '1.2.3'], run.calls.find { it.name == 'deploy' }.arguments)
        assertTrue(run.commands.isEmpty())
        assertEquals(0, run.approvals)
    }

    @Test
    void skipE2ERetainsTheOtherGatewayReleaseSteps() {
        def run = new PipelineSimulation(target: 'gateway', skipE2E: true).execute()
        assertNull(run.failure)
        assertTrue(run.argumentsFor('e2e').isEmpty())
        assertEquals(['dev', 'stage', 'prod'], run.argumentsFor('deploy'))
    }
}

class PipelineSimulation extends GroovyObjectSupport {
    String target = 'auto'
    String branch = 'main'
    String rollbackTag = ''
    boolean skipE2E
    boolean failValidation
    List changedFiles = []
    Map env = [GIT_COMMIT: 'abc1234']
    Map currentBuild = [:]
    List<Map> calls = []
    List<String> commands = []
    List<String> messages = []
    List<String> executedStages = []
    List<Map> definitions = []
    Map posts = [:]
    int approvals
    Throwable failure
    boolean allowed = true
    String currentStage
    boolean collecting

    PipelineSimulation execute() {
        env.BRANCH_NAME = branch
        def library = new Expando()
        ['buildAndTest', 'sonarScan', 'qualityGate', 'publish', 'dockerBuildPush', 'deploy', 'e2e', 'notify'].each { name ->
            library[name] = { Object... args ->
                calls << [name: name, arguments: args.toList()]
                if (name == 'buildAndTest' && failValidation) {
                    throw new RuntimeException('Gradle test/coverage failed')
                }
            }
        }
        def binding = new Binding([
                env: env, params: [TARGET_SERVICE: target, TAG: '', ROLLBACK_TAG: rollbackTag, SKIP_E2E: skipE2E],
                currentBuild: currentBuild, scm: [:], any: 'any', ci: library])
        binding.setVariable('pipeline', { Closure body -> pipeline(body) })
        File root = new File(System.getProperty('repository.root'))
        // @Library is resolved by Jenkins; the same ci methods are provided by the double above.
        String source = new File(root, 'Jenkinsfile').readLines().findAll { !it.startsWith('@Library(') }.join('\n')
        new GroovyShell(binding).evaluate(source, 'Jenkinsfile')
        this
    }

    List argumentsFor(String name) {
        calls.findAll { it.name == name }.collect { it.arguments ? it.arguments[0] : null }
    }

    void pipeline(Closure body) {
        invoke(body)
        try {
            definitions.each { definition ->
                currentStage = definition.name
                allowed = true
                invoke(definition.body)
            }
        } catch (Throwable error) {
            failure = error
        }
        Closure post = posts[failure ? 'failure' : 'success']
        if (post) { invoke(post) }
    }

    void agent(Object ignored) {}
    void options(Closure ignored) {}
    void triggers(Closure ignored) {}
    void parameters(Closure ignored) {}

    void environment(Closure body) {
        def delegate = new EnvironmentValues(values: env)
        def scoped = body.rehydrate(delegate, body.owner, body.thisObject)
        scoped.resolveStrategy = Closure.DELEGATE_ONLY
        scoped()
    }

    void stages(Closure body) {
        collecting = true
        invoke(body)
        collecting = false
    }

    void stage(String name, Closure body) {
        assert collecting
        definitions << [name: name, body: body]
    }

    void when(Closure body) {
        def conditions = new Conditions(branch: branch)
        invoke(body, conditions)
        allowed = conditions.values.every { it }
    }

    void steps(Closure body) {
        if (allowed) {
            executedStages << currentStage
            invoke(body)
        }
    }

    void script(Closure body) { invoke(body) }
    void checkout(Object ignored) {}
    void echo(Object message) { messages << message.toString() }
    void sh(Object command) { commands << command.toString() }
    void input(Map ignored) { approvals++ }
    Map usernamePassword(Map values) { values }
    void withCredentials(List ignored, Closure body) { invoke(body) }

    Object load(String path) {
        def registry = new GroovyShell().parse(new File(System.getProperty('repository.root'), path))
        registry.run()
        registry.metaClass.detectChanges = { Map ignored -> [files: changedFiles, reliable: true] }
        registry
    }

    Object withEnv(List<String> values, Closure body) {
        def previous = new LinkedHashMap(env)
        values.each { value ->
            def pair = value.split('=', 2)
            env[pair[0]] = pair[1]
        }
        try { invoke(body) } finally { env.clear(); env.putAll(previous) }
    }

    void post(Closure body) { invoke(body, new PostConditions(values: posts)) }

    private Object invoke(Closure body, Object delegate = this) {
        def scoped = body.rehydrate(delegate, body.owner, body.thisObject)
        scoped.resolveStrategy = Closure.DELEGATE_FIRST
        scoped()
    }
}

class EnvironmentValues extends GroovyObjectSupport {
    Map values
    void propertyMissing(String name, Object value) { values[name] = value }
}

class Conditions extends GroovyObjectSupport {
    String branch
    List<Boolean> values = []
    void expression(Closure body) { values << (body() as boolean) }
    void branch(String expected) { values << (branch == expected) }
    void allOf(Closure body) {
        def nested = new Conditions(branch: branch)
        def scoped = body.rehydrate(nested, body.owner, body.thisObject)
        scoped.resolveStrategy = Closure.DELEGATE_FIRST
        scoped()
        values << nested.values.every { it }
    }
}

class PostConditions extends GroovyObjectSupport {
    Map values
    void success(Closure body) { values.success = body }
    void failure(Closure body) { values.failure = body }
}
