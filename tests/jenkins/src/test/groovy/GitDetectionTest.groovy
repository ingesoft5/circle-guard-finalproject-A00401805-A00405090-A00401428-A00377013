import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class GitDetectionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()
    File checkout
    Script registry
    String initialCommit
    Map environment = [:]

    @Before
    void setUp() {
        def remote = temporary.newFolder('remote')
        command(remote, ['git', 'init', '--bare'])
        checkout = temporary.newFolder('checkout')
        git('init', '-b', 'main')
        git('config', 'user.name', 'Pipeline Test')
        git('config', 'user.email', 'pipeline@example.invalid')
        write('services/circleguard-gateway-service/src/main/resources/original.txt', 'original')
        initialCommit = commit()
        git('remote', 'add', 'origin', remote.absolutePath)
        git('push', 'origin', 'main')

        def binding = new Binding()
        binding.setVariable('withEnv', { List values, Closure body ->
            def saved = new LinkedHashMap(environment)
            values.each { String value ->
                def pair = value.split('=', 2)
                environment[pair[0]] = pair[1]
            }
            try { body() } finally { environment.clear(); environment.putAll(saved) }
        })
        binding.setVariable('sh', { Map options ->
            def result = command(checkout, ['sh', '-c', options.script], environment)
            if (options.returnStatus) { return result.status }
            if (result.status != 0) { throw new RuntimeException(result.output) }
            options.returnStdout ? result.output : null
        })
        registry = new GroovyShell(binding).parse(new File(System.getProperty('repository.root'), 'jenkins/services.groovy'))
        registry.run()
    }

    @Test
    void comparesTheEntirePrEvenAfterAnEarlierSuccessfulBuild() {
        git('switch', '-c', 'feature/form')
        write('services/circleguard-form-service/src/main/java/Survey.java', 'first change')
        String earlierBuild = commit()
        write('services/circleguard-gateway-service/src/main/java/Gate.java', 'second change')
        commit()
        def result = registry.detectChanges([branch: 'PR-99', changeTarget: 'main', previousSuccess: earlierBuild])
        assertTrue(result.reliable)
        assertEquals(['gateway', 'form'], registry.selectServices('auto', result.files, true).services)
    }

    @Test
    void mainIncludesAllChangesSinceTheLastSuccessfulRevision() {
        write('services/circleguard-form-service/src/main/java/Survey.java', 'form')
        commit()
        write('services/circleguard-gateway-service/src/main/java/Gate.java', 'gateway')
        commit()
        def result = registry.detectChanges([branch: 'main', previousSuccess: initialCommit,
                                             previousDescription: '[CI:auto] Servicios: gateway, form'])
        assertTrue(result.reliable)
        assertEquals(2, result.files.size())
    }

    @Test
    void branchBuildUsesMainAndPreservesBothSidesOfARename() {
        git('switch', '-c', 'feature/move')
        new File(checkout, 'services/circleguard-form-service/src/main/resources').mkdirs()
        git('mv', 'services/circleguard-gateway-service/src/main/resources/original.txt',
                'services/circleguard-form-service/src/main/resources/moved file.txt')
        commit()
        def result = registry.detectChanges([branch: 'feature/move'])
        assertTrue(result.reliable)
        assertTrue(result.files.contains('services/circleguard-form-service/src/main/resources/moved file.txt'))
        assertEquals(['gateway', 'form'], registry.selectServices('auto', result.files, true).services)
    }

    @Test
    void missingOrNonexistentMainBaselineRequiresFullValidation() {
        assertFalse(registry.detectChanges([branch: 'main']).reliable)
        assertFalse(registry.detectChanges([branch: 'main', previousSuccess: '0' * 40,
                                            previousDescription: '[CI:auto] Servicios: gateway, form']).reliable)
    }

    @Test
    void failedTargetFetchCannotReportNoChanges() {
        def result = registry.detectChanges([branch: 'PR-99', changeTarget: 'missing'])
        assertFalse(result.reliable)
        assertEquals(['gateway', 'form'], registry.selectServices('auto', result.files, result.reliable).services)
    }

    @Test
    void anUnchangedSuccessfulMainDoesNotBuildAgain() {
        def result = registry.detectChanges([branch: 'main', previousSuccess: initialCommit,
                                             previousDescription: '[CI:all] Servicios: gateway, form'])
        assertTrue(result.reliable)
        assertTrue(result.files.isEmpty())
    }

    @Test
    void selectiveManualAndLegacyBuildsCannotHideUnvalidatedChanges() {
        ['[CI:form] Servicios: form', '[CI:gateway] Servicios: gateway',
         '[CI:rollback] Gateway: 1.0.0', 'Gateway previo a esta adaptación'].each { description ->
            def result = registry.detectChanges([branch: 'main', previousSuccess: initialCommit,
                                                 previousDescription: description])
            assertFalse(description, result.reliable)
        }
    }

    @Test(expected = InterruptedException)
    void cancellationDoesNotStartAFullBuildInstead() {
        registry.binding.setVariable('sh', { Map ignored -> throw new InterruptedException('cancelled') })
        registry.detectChanges([branch: 'PR-99', changeTarget: 'main'])
    }

    private void write(String path, String text) {
        def file = new File(checkout, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private String commit() {
        git('add', '.')
        git('commit', '-m', 'test fixture')
        git('rev-parse', 'HEAD').trim()
    }

    private String git(String... args) {
        def result = command(checkout, ['git'] + args.toList())
        assertEquals(result.output, 0, result.status)
        result.output
    }

    private Map command(File directory, List<String> args, Map extraEnvironment = [:]) {
        def builder = new ProcessBuilder(args).directory(directory).redirectErrorStream(true)
        builder.environment().putAll(extraEnvironment)
        def process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        [status: process.waitFor(), output: output]
    }
}
