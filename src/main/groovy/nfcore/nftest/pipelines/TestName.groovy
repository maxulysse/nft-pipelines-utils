package nfcore.nftest.pipelines

/**
 * Builds a test name that reads like the command line:
 * `-profile test --tools vep --vep_loftee -stub`
 */
class TestName {

    static String generate(Map scenario, Map definition) {
        def hidden = Scenario.list((definition.naming as Map)?.hideParams) as Set
        def profile = scenario.profile ?: definition.profile ?: 'test'
        def parts = ["-profile ${profile}".toString()]
        ((scenario.params ?: [:]) as Map).each { key, value ->
            if (!(key in hidden)) {
                parts << param(key as String, value)
            }
        }
        if (scenario.stub) {
            parts << '-stub'
        }
        return parts.join(' ')
    }

    static String param(String key, Object value) {
        if (value == true) {
            return "--${key}"
        }
        return "--${key} ${format(value)}"
    }

    static String format(Object value) {
        if (value == null) {
            return 'null'
        }
        if (value instanceof Collection) {
            return value.collect { format(it) }.join(',')
        }
        def text = value.toString()
        // paths and URLs: the file name is what tells tests apart
        return text.contains('/') ? text.tokenize('/').last() : text
    }
}
