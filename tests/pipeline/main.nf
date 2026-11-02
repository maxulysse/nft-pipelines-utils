// Minimal pipeline used to test nft-pipelines-utils
params.outdir   = null
params.greeting = 'hello'
params.fail     = false
params.input    = null

process SAY {
    publishDir "${params.outdir}/say", mode: 'copy'

    input:
    val greeting

    output:
    path 'out.txt'
    path 'table.csv'
    path 'run.log'

    script:
    """
    echo ${greeting} > out.txt
    printf 'b,2.0000001\\na,1\\n' > table.csv
    date > run.log
    """

    stub:
    "touch out.txt table.csv run.log"
}

workflow {
    if (params.fail) {
        error "Failing on purpose: --fail was set"
    }
    if (params.input) {
        log.info "Input file: ${file(params.input).text.trim()}"
    }
    log.warn "Greeting with ${params.greeting}"
    SAY(params.greeting)
}
