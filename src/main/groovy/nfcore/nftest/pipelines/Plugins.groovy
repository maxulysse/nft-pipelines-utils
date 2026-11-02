package nfcore.nftest.pipelines

/**
 * Finds the classes of other nf-test plugins at runtime.
 *
 * nf-test resolves calls inside test closures on its own context only, so functions from
 * other plugins (nft-utils, nft-bam) are not visible from here by their bare name.
 */
class Plugins {

    static Class nftUtils() {
        return find('nfcore.nftest.utils.Methods', 'nft-utils')
    }

    static Class nftBam() {
        return find('nvnieuwk.nf.test.bam.Methods', 'nft-bam')
    }

    static Class find(String className, String plugin) {
        def loaders = [Plugins.classLoader, Thread.currentThread().contextClassLoader]
        for (loader in loaders) {
            try {
                return Class.forName(className, true, loader)
            } catch (ClassNotFoundException ignored) {
                // try the next loader
            }
        }
        throw new IllegalStateException("nft-pipelines-utils needs the ${plugin} plugin: add `load \"${plugin}@<version>\"` to the plugins block of nf-test.config")
    }
}
