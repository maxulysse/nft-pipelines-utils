package nfcore.nftest.pipelines

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Builds the ordered list handed to snapshot(). The order is fixed so snapshots stay comparable.
 */
class Snapshot {

    static List build(Object ctx, Map o) {
        def outdir = ctx.params.outdir.toString()
        def utils = Plugins.nftUtils()
        def assertion = []

        // a failed run has no meaningful task count or versions file
        if (!o.failure) {
            assertion << ctx.workflow.trace.succeeded().size()
            if (o.versionsFile) {
                def versions = "${outdir}/${o.versionsFile}".toString()
                assertion << ((new File(versions).exists() ? utils.removeFromYamlMap(versions, 'Workflow') : null) ?: 'No versions')
            }
        }

        // pipeline_info/ is always created, so this list is never empty
        assertion << utils.getAllFilesFromDir([relative: true, includeDir: true, ignore: o.ignorePatterns] as LinkedHashMap, outdir)

        // stub runs produce empty placeholder files: hashing them is noise
        if (!o.stub) {
            def files = utils.getAllFilesFromDir([relative: true, includeDir: false, ignore: o.ignorePatterns] as LinkedHashMap, outdir)
            def content = contents(ctx, o, outdir, files.collect { it.toString() })
            assertion << (content ?: 'No stable content')
        }

        if (o.extraAssertions) {
            assertion << o.extraAssertions.call(outdir, ctx.workflow)
        }

        def output = ctx.workflow.stderr + ctx.workflow.stdout
        assertion << (utils.filterNextflowOutput([ignore: o.logIgnore, include: o.logInclude] as LinkedHashMap, output) ?: 'No warnings')

        if (o.snapshotStd) {
            def std = o.snapshotStd.collect { ctx.workflow."$it" }.flatten()
            def filter = [ignore: o.logIgnore] as LinkedHashMap
            if (o.stdInclude) {
                filter.include = o.stdInclude
            }
            assertion << (utils.filterNextflowOutput(filter, std) ?: 'No content')
        }

        return assertion
    }

    /** One entry per file with stable content, labelled with how it was checked. */
    static List contents(Object ctx, Map o, String outdir, List<String> files) {
        def unstable = matchers(o.unstablePatterns + ignoreFileLines(o.ignoreFile))
        def layers = o.treatments.collectEntries { layer, treatments ->
            [(layer): treatments.collectEntries { treatment, patterns -> [(treatment): matchers(patterns)] }]
        }

        return files.findResults { rel ->
            def file = "${outdir}/${rel}".toString()
            def treatment = treatmentFor(rel, layers.scenario as Map) ?: treatmentFor(rel, layers.definition as Map)
            if (treatment) {
                return treat(treatment, file, o)
            }
            if (matches(rel, unstable)) {
                return null
            }
            // same type nf-test's path() returns: snapshotted as name:md5,<hash>
            return Paths.get(file)
        }.flatten()
    }

    static Object treat(String treatment, String file, Map o) {
        def name = file.tokenize('/').last()
        switch (treatment) {
            case 'readsMD5Patterns':
                return "${name}:readsMD5,${alignment(file, o).readsMD5}".toString()
            case 'readsStatsPatterns':
                return "${name}:readsStats,${alignment(file, o).getStatistics()}".toString()
            case 'variantsMD5Patterns':
                return withHeader(["${name}:variantsMD5,${vcf(file).variantsMD5}".toString()], name, file, o)
            case 'variantsSummaryPatterns':
                return withHeader(["${name}:variantsSummary,${vcf(file).summary}".toString()], name, file, o)
            case 'tableMD5Patterns':
                def table = Plugins.nftUtils().normalizeTable(Paths.get(file), o.tableDoubleDigits as int, o.tableRemoveComments as boolean)
                return "${name}:tableMD5,${table.md5()}".toString()
            default:
                throw new IllegalStateException("nft-pipelines-utils: unhandled treatment ${treatment}")
        }
    }

    static List withHeader(List entries, String name, String file, Map o) {
        if (o.vcfHeaderContains) {
            def found = vcf(file).header.toString().contains(o.vcfHeaderContains as String)
            entries << "${name}:header.contains(${o.vcfHeaderContains}),${found}".toString()
        }
        return entries
    }

    static Object alignment(String file, Map o) {
        def bam = Plugins.nftBam()
        if (file.endsWith('.cram')) {
            if (!o.referenceFasta) {
                throw new IllegalArgumentException("nft-pipelines-utils: ${file} is a CRAM: set snapshot.referenceFasta or the scenario's referenceFasta")
            }
            return bam.cram(file, o.referenceFasta as String)
        }
        return file.endsWith('.sam') ? bam.sam(file) : bam.bam(file)
    }

    static Object vcf(String file) {
        try {
            return Paths.get(file).vcf
        } catch (MissingPropertyException e) {
            throw new IllegalStateException('nft-pipelines-utils needs the nft-vcf plugin for variant patterns: add `load "nft-vcf@<version>"` to nf-test.config', e)
        }
    }

    static String treatmentFor(String rel, Map<String, List<PathMatcher>> treatments) {
        def hits = treatments.findAll { treatment, patterns -> matches(rel, patterns) }.keySet()
        if (hits.size() > 1) {
            throw new IllegalArgumentException("nft-pipelines-utils: ${rel} matches several of ${hits} at the same level. Make the patterns exclusive.")
        }
        return hits ? hits.first() : null
    }

    static List<String> ignoreFileLines(String ignoreFile) {
        def file = new File(ignoreFile)
        if (!file.exists()) {
            return []
        }
        return file.readLines()*.trim().findAll { it && !it.startsWith('#') }
    }

    static List<PathMatcher> matchers(List<String> patterns) {
        def fs = FileSystems.getDefault()
        return patterns.collectMany { pattern ->
            // `**/x` should also match x at the top level, as in .gitignore
            def variants = pattern.startsWith('**/') ? [pattern, pattern.substring(3)] : [pattern]
            variants.collect { fs.getPathMatcher("glob:${it}") }
        }
    }

    static boolean matches(String rel, List<PathMatcher> matchers) {
        Path path = Paths.get(rel)
        return matchers.any { it.matches(path) }
    }
}
