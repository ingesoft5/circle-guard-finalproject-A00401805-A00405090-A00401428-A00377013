import org.junit.Before
import org.junit.Test

import static org.junit.Assert.*

class ServiceSelectionTest {
    Script registry

    @Before
    void loadRegistry() {
        registry = new GroovyShell().parse(new File(System.getProperty('repository.root'), 'jenkins/services.groovy'))
        registry.run()
    }

    @Test
    void selectsOnlyTheChangedActiveService() {
        assertEquals(['form'], select(['services/circleguard-form-service/src/main/java/Survey.java']).services)
        assertEquals(['gateway'], select(['services/circleguard-gateway-service/src/test/java/GateTest.java']).services)
    }

    @Test
    void selectsBothWithoutRepeatingServices() {
        def result = select(['services/circleguard-form-service/build.gradle.kts',
                             'services/circleguard-form-service/src/main/resources/application.yml',
                             'services/circleguard-gateway-service/Dockerfile'])
        assertEquals(['gateway', 'form'], result.services)
    }

    @Test
    void promotionAlsoValidatesItsFormConsumerTest() {
        def result = select(['services/circleguard-promotion-service/src/main/java/SurveyListener.java'])
        assertEquals(['gateway', 'form'], result.services)
        assertEquals(['promotion'], result.pending)
    }

    @Test
    void pendingServicesKeepGatewayAndAnExplicitWarning() {
        def result = select(['services/circleguard-identity-service/src/main/java/Identity.java'])
        assertEquals(['gateway'], result.services)
        assertEquals(['identity'], result.pending)
        assertFalse(result.reasons.isEmpty())
        assertEquals(['web'], select(['mobile/package.json']).pending)
    }

    @Test
    void sharedBuildAndPipelineChangesSelectAllActiveServices() {
        ['Jenkinsfile', 'vars/ci.groovy', 'jenkins/services.groovy', 'gradle/wrapper/gradle-wrapper.properties',
         'build.gradle.kts', 'settings.gradle.kts'].each { path ->
            assertEquals(path, ['gateway', 'form'], select([path]).services)
        }
    }

    @Test
    void documentationSkipsBuildsButResourcesAndFixturesStillRun() {
        assertTrue(select(['README.md', 'docs/infra/ci-cd-setup.md',
                           'services/circleguard-form-service/README.md']).services.isEmpty())
        assertEquals(['form'], select(['services/circleguard-form-service/src/test/resources/README.md']).services)
        assertEquals(['form'], select(['services/circleguard-form-service/src/main/resources/options.txt']).services)
    }

    @Test
    void gatewayInfrastructureAndE2EKeepTheGatewayWorkflow() {
        ['sonar-project.properties', 'tests/e2e/access-validation.spec.ts',
         'deploy/scripts/deploy.sh'].each { path ->
            assertEquals(['gateway'], select([path]).services)
        }
    }

    @Test
    void unknownPathsOrUnreliableHistoryCannotSilentlySkipTests() {
        assertEquals(['gateway', 'form'], select(['unknown-config.yml']).services)
        assertEquals(['gateway', 'form'], registry.selectServices('auto', [], false).services)
        assertTrue(select([]).services.isEmpty())
    }

    @Test
    void manualSelectionOverridesDetectionAndAllListsPendingModules() {
        assertEquals(['form'], registry.selectServices('form', [], false).services)
        assertEquals(['gateway'], registry.selectServices('gateway', [], false).services)
        def result = registry.selectServices('all', [], true)
        assertEquals(['gateway', 'form'], result.services)
        assertEquals(['auth', 'identity', 'promotion', 'notification', 'file', 'dashboard', 'web'], result.pending)
    }

    @Test(expected = IllegalArgumentException)
    void cannotManuallyClaimAPendingServiceIsIntegrated() {
        registry.selectServices('identity', [], true)
    }

    private Map select(List paths) {
        registry.selectServices('auto', paths, true)
    }
}
