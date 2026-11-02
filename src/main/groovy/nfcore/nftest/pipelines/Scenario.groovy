package nfcore.nftest.pipelines

/**
 * Merges a scenario with the pipeline definition into the resolved options a test runs with.
 */
class Scenario {

    /** How a matching file is snapshotted instead of a plain md5. */
    static final List<String> TREATMENTS = [
        'readsMD5Patterns',
        'readsStatsPatterns',
        'variantsMD5Patterns',
        'variantsSummaryPatterns',
        'csvMD5Patterns',
    ]

    static final Set<String> KEYS = ([
        'name', 'profile', 'params', 'stub', 'failure', 'tag', 'noConda', 'options',
        'ignorePatterns', 'unstablePatterns', 'referenceFasta', 'csvDoubleDigits', 'vcfHeaderContains',
        'logIgnore', 'logInclude', 'snapshotStd', 'stdInclude',
        'extraAssertions', 'setup',
    ] + TREATMENTS) as Set

    /** Log lines that vary between runs, machines and Nextflow versions. */
    static final List<String> LOG_IGNORE = [
        'Check script',
        'Creating env using',
        'Downloading plugin',
        'Got an interrupted exception while taking agent result',
        'Pulling Singularity image',
        'Staging foreign file',
        'Unable to resume cached task',
        'Unable to stage foreign file',
    ]

    static Map resolve(Map scenario, Map definition) {
        def snapshotDef = (definition.snapshot ?: [:]) as Map
        def logsDef = (definition.logs ?: [:]) as Map
        def prefixes = ((definition.tags as Map)?.prefixes ?: ['gpu']) as List<String>

        def unknown = scenario.keySet() - KEYS - prefixes
        if (unknown) {
            throw new IllegalArgumentException("nft-pipelines-utils: unknown scenario key(s) ${unknown}. Allowed: ${(KEYS + prefixes).sort().join(', ')}")
        }

        def pipeline = definition.pipeline as String
        def resolved = [
            pipeline         : pipeline,
            profile          : scenario.profile,
            params           : (scenario.params ?: [:]) as Map,
            stub             : scenario.stub as boolean,
            failure          : scenario.failure as boolean,
            options          : scenario.options,
            versionsFile     : definition.versionsFile ?: (pipeline ? "pipeline_info/nf_core_${pipeline}_software_mqc_versions.yml".toString() : null),
            ignoreFile       : snapshotDef.ignoreFile ?: 'tests/.nftignore',
            ignorePatterns   : list(snapshotDef.ignorePatterns ?: ['pipeline_info/*.{html,json,txt}']) + list(scenario.ignorePatterns),
            unstablePatterns : list(snapshotDef.unstablePatterns) + list(scenario.unstablePatterns),
            // scenario treatments are checked before the definition ones, so a scenario can override them
            treatments       : [scenario: treatments(scenario), definition: treatments(snapshotDef)],
            referenceFasta   : scenario.referenceFasta ?: snapshotDef.referenceFasta,
            csvDoubleDigits  : (scenario.csvDoubleDigits ?: snapshotDef.csvDoubleDigits ?: 6) as int,
            vcfHeaderContains: scenario.vcfHeaderContains,
            logIgnore        : LOG_IGNORE + list(logsDef.ignore) + list(scenario.logIgnore),
            logInclude       : list(scenario.logInclude ?: logsDef.include ?: ['WARN']),
            snapshotStd      : list(scenario.snapshotStd),
            stdInclude       : list(scenario.stdInclude),
            extraAssertions  : scenario.extraAssertions as Closure,
            setup            : scenario.setup as Closure,
        ]

        def badStd = resolved.snapshotStd - ['stdout', 'stderr']
        if (badStd) {
            throw new IllegalArgumentException("nft-pipelines-utils: snapshotStd only takes 'stdout' and 'stderr', got ${badStd}")
        }

        resolved.tags = tags(scenario, pipeline, prefixes)
        resolved.name = scenario.name ?: TestName.generate(scenario, definition)
        return resolved
    }

    static List<String> tags(Map scenario, String pipeline, List<String> prefixes) {
        def prefix = prefixes.find { scenario[it] } ?: 'cpu'
        def tags = ['pipeline']
        if (pipeline) {
            tags << "pipeline_${pipeline}".toString()
        }
        tags << "${prefix}${scenario.noConda ? '' : '_conda'}${scenario.stub ? '_stub' : ''}".toString()
        tags.addAll(list(scenario.tag))
        if (scenario.failure) {
            tags << 'failure'
        }
        return tags
    }

    static Map<String, List<String>> treatments(Map source) {
        return TREATMENTS.findAll { source[it] }.collectEntries { [(it): list(source[it])] }
    }

    static List<String> list(Object value) {
        if (value == null) {
            return []
        }
        return (value instanceof Collection ? value : [value]).collect { it.toString() }
    }
}
