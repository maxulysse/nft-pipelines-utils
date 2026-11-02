package nfcore.nftest.pipelines

/**
 * Public API of the nft-pipelines-utils nf-test plugin.
 *
 * Registered via META-INF/nf-test-plugin, so these are callable from any nf-test file.
 */
class Methods {

    /**
     * Registers one nf-test test per scenario on the given test suite.
     *
     * Usage, inside `nextflow_pipeline { }`: `pipelineTests(delegate, scenarios)`
     */
    static void pipelineTests(Object suite, List<Map> scenarios) {
        def names = [] as Set
        scenarios.each { scenario ->
            def resolved = Scenario.resolve(scenario, Definition.load())
            if (!names.add(resolved.name)) {
                throw new IllegalArgumentException("nft-pipelines-utils: two scenarios share the test name '${resolved.name}'. Set `name:` on one of them.")
            }
            suite.test(resolved.name, TestBody.build(resolved))
        }
    }

    /**
     * Returns [name, closure] for one scenario.
     *
     * Usage: `scenarios.each { s -> test(*pipelineTest(s)) }`
     */
    static List pipelineTest(Map scenario) {
        def resolved = Scenario.resolve(scenario, Definition.load())
        return [resolved.name, TestBody.build(resolved)]
    }

    /** Returns the test name generated for a scenario. */
    static String pipelineTestName(Map scenario) {
        return Scenario.resolve(scenario, Definition.load()).name
    }
}
