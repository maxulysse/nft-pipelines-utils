package nfcore.nftest.pipelines

/**
 * The nf-test test body for one resolved scenario.
 *
 * nf-test resolves calls in these closures on its own context only:
 * call plugin code through its class (Snapshot.build), never by bare name.
 */
class TestBody {

    static Closure build(Map o) {
        return {
            o.tags.each { tag it }

            if (o.profile) {
                profile o.profile
            }

            options "-output-dir ${outputDir}${o.stub ? ' -stub' : ''}${o.options ? ' ' + o.options : ''}"

            if (o.setup) {
                setup {
                    o.setup.call(launchDir)
                }
            }

            when {
                params {
                    outdir = "${outputDir}"
                    o.params.each { key, value -> delegate."$key" = value }
                }
            }

            then {
                // fail early on the run status so a failed run does not print a huge snapshot diff
                if (o.failure) {
                    assert workflow.failed
                } else {
                    assert workflow.success
                }
                assert snapshot(*Snapshot.build(delegate, o)).match()
            }

            cleanup {
                if (System.getenv('NFT_CLEANUP')) {
                    println "NFT_CLEANUP set: deleting ${workDir}"
                    new File("${workDir}").deleteDir()
                }
            }
        }
    }
}
