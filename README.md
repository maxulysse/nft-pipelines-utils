# nft-pipelines-utils

nf-test plugin for scenario-driven pipeline-level tests. It replaces the `tests/lib/UTILS.groovy` copied between nf-core pipelines.

Each pipeline describes what its tests snapshot once, in `tests/nft-pipeline.yml`. Test files list scenarios.

## Usage

`nf-test.config`:

```groovy
plugins {
    load "nft-utils@1.2.1"       // required
    load "nft-bam@0.6.1"         // for reads*Patterns
    load "nft-vcf@1.0.7"         // for variants*Patterns
}
```

`tests/default.nf.test`:

```groovy
nextflow_pipeline {

    name "Test pipeline"
    script "../main.nf"

    def scenarios = [
        [ params: [:] ],
        [ params: [ tools: 'vep', vep_loftee: true ], vcfHeaderContains: '|LoF|' ],
        [ params: [:], stub: true ],
    ]

    pipelineTests(delegate, scenarios)
}
```

Test names are generated from the scenario: `-profile test --tools vep --vep_loftee`. Set `name:` to override.

## Definition file: `tests/nft-pipeline.yml`

```yaml
pipeline: rnavar                 # tags pipeline + pipeline_rnavar; versions file nf_core_rnavar_software_mqc_versions.yml
profile: test                    # profile shown in test names

snapshot:
  ignoreFile: tests/.nftignore   # file names only, no md5 (default)
  ignorePatterns: ['pipeline_info/*.{html,json,txt}']   # left out entirely (default)
  readsMD5Patterns: ['**/*.{bam,cram}']
  variantsMD5Patterns: ['**/*.vcf{,.gz}']
  referenceFasta: https://.../genome.fasta   # for CRAM

logs:
  include: [WARN]                # lines snapshotted from stdout + stderr (default)
  ignore: ["Some unstable warning"]

tags:
  prefixes: [gpu]                # scenario flags giving the gpu|cpu tag prefix (default)

naming:
  hideParams: [input, fasta]     # params left out of generated names
```

Set `NFT_PIPELINE_DEFINITION` to read it from elsewhere.

## Scenario keys

| Key | Effect |
| --- | --- |
| `name` | Test name. Generated from `profile`, `params` and `stub` when absent |
| `profile` | nf-test profile for this test |
| `params` | Pipeline params |
| `stub` | Run with `-stub`. Content is not snapshotted |
| `failure` | Expect the run to fail |
| `tag` | Extra tag(s) |
| `noConda` | Tag `cpu` instead of `cpu_conda` |
| `options` | Extra Nextflow options |
| `ignorePatterns`, `unstablePatterns` | Added to the definition ones |
| `readsMD5Patterns`, `readsStatsPatterns` | BAM/CRAM/SAM reads md5 or statistics (nft-bam) |
| `variantsMD5Patterns`, `variantsSummaryPatterns` | VCF variants md5 or summary (nft-vcf) |
| `csvMD5Patterns` | md5 of the normalized table (needs an nft-utils release with `normalizeCsv`) |
| `vcfHeaderContains` | Snapshot whether each VCF header contains this string |
| `referenceFasta`, `csvDoubleDigits` | As in nft-utils `sanitizeOutput` |
| `logIgnore`, `logInclude` | Log filtering, added to / replacing the definition |
| `snapshotStd`, `stdInclude` | Also snapshot `stdout` and/or `stderr` lines |
| `extraAssertions` | `{ outdir, workflow -> ... }`, its result is added to the snapshot |
| `setup` | `{ launchDir -> ... }`, run in nf-test `setup` |

Pattern keys set in a scenario win over the definition for the files they match. A file matching two pattern keys at the same level is an error. Unknown keys are an error.

Snapshot order: succeeded tasks, versions, file names, file contents, extra assertions, log lines, std lines.

Set `NFT_CLEANUP` to delete each test's work directory after it runs.

## Development

```bash
mvn -B clean package
nf-test test --plugins target/nft-pipelines-utils-*.jar --verbose --profile=+docker tests/
```

Run nf-test with `--verbose`: without it, nf-test does not capture the Nextflow stderr, so log lines are missing from snapshots.
