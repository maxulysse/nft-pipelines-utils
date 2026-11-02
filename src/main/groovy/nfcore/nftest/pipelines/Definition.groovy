package nfcore.nftest.pipelines

import groovy.yaml.YamlSlurper

/**
 * The pipeline test definition: tests/nft-pipeline.yml, relative to where nf-test runs.
 *
 * Override the location with the NFT_PIPELINE_DEFINITION environment variable.
 */
class Definition {

    static final String DEFAULT_PATH = 'tests/nft-pipeline.yml'

    static final Map<String, Set<String>> KEYS = [
        pipeline     : [] as Set,
        profile      : [] as Set,
        versionsFile : [] as Set,
        snapshot     : ['ignoreFile', 'ignorePatterns', 'unstablePatterns', 'referenceFasta', 'tableDoubleDigits', 'tableRemoveComments'] as Set
                        + (Scenario.TREATMENTS as Set),
        logs         : ['include', 'ignore'] as Set,
        tags         : ['prefixes'] as Set,
        naming       : ['hideParams'] as Set,
    ]

    private static final Map<String, Map> CACHE = [:]

    static Map load() {
        def path = System.getenv('NFT_PIPELINE_DEFINITION') ?: "${System.getProperty('user.dir')}/${DEFAULT_PATH}".toString()
        if (!CACHE.containsKey(path)) {
            def file = new File(path)
            def definition = file.exists() ? (new YamlSlurper().parse(file) ?: [:]) as Map : [:]
            validate(definition, path)
            CACHE[path] = definition
        }
        return CACHE[path]
    }

    static void validate(Map definition, String path) {
        definition.each { key, value ->
            if (!KEYS.containsKey(key)) {
                throw new IllegalArgumentException("nft-pipelines-utils: unknown key '${key}' in ${path}. Allowed: ${KEYS.keySet().join(', ')}")
            }
            def allowed = KEYS[key]
            if (allowed && value instanceof Map) {
                def unknown = (value as Map).keySet() - allowed
                if (unknown) {
                    throw new IllegalArgumentException("nft-pipelines-utils: unknown key(s) ${unknown} under '${key}' in ${path}. Allowed: ${allowed.join(', ')}")
                }
            }
        }
    }
}
