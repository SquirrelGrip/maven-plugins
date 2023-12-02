package com.github.squirrelgrip.plugin.gitflow

class GitFlowConfig {
    companion object {
        const val DEFAULT_PRODUCTION_BRANCH: String = "master"
        const val DEFAULT_DEVELOPMENT_BRANCH: String = "develop"
        const val DEFAULT_FEATURE_BRANCH_PREFIX: String = "feature/"
        const val DEFAULT_RELEASE_BRANCH_PREFIX: String = "release/"
        const val DEFAULT_HOTFIX_BRANCH_PREFIX: String = "hotfix/"
        const val DEFAULT_SUPPORT_BRANCH_PREFIX: String = "support/"
        const val DEFAULT_VERSION_TAG_PREFIX: String = ""
        const val DEFAULT_ORIGIN: String = "origin"
    }

    /** Name of the production branch.  */
    var productionBranch: String = DEFAULT_PRODUCTION_BRANCH

    /** Name of the development branch.  */
    var developmentBranch: String = DEFAULT_DEVELOPMENT_BRANCH

    /** Prefix of the feature branch.  */
    var featureBranchPrefix: String = DEFAULT_FEATURE_BRANCH_PREFIX

    /** Prefix of the release branch.  */
    var releaseBranchPrefix: String = DEFAULT_RELEASE_BRANCH_PREFIX

    /** Prefix of the hotfix branch.  */
    var hotfixBranchPrefix: String = DEFAULT_HOTFIX_BRANCH_PREFIX

    /** Prefix of the support branch.  */
    var supportBranchPrefix: String = DEFAULT_SUPPORT_BRANCH_PREFIX

    /** Prefix of the version tag.  */
    var versionTagPrefix: String = DEFAULT_VERSION_TAG_PREFIX

    /** Name of the default remote.  */
    var origin: String = DEFAULT_ORIGIN

    init {
        productionBranch = System.getProperty("gitFlowConfig.productionBranch", DEFAULT_PRODUCTION_BRANCH)
        developmentBranch = System.getProperty("gitFlowConfig.developmentBranch", DEFAULT_DEVELOPMENT_BRANCH)
        featureBranchPrefix = System.getProperty("gitFlowConfig.featureBranchPrefix", DEFAULT_FEATURE_BRANCH_PREFIX)
        releaseBranchPrefix = System.getProperty("gitFlowConfig.releaseBranchPrefix", DEFAULT_RELEASE_BRANCH_PREFIX)
        hotfixBranchPrefix = System.getProperty("gitFlowConfig.hotfixBranchPrefix", DEFAULT_HOTFIX_BRANCH_PREFIX)
        supportBranchPrefix = System.getProperty("gitFlowConfig.supportBranchPrefix", DEFAULT_SUPPORT_BRANCH_PREFIX)
        versionTagPrefix = System.getProperty("gitFlowConfig.versionTagPrefix", DEFAULT_VERSION_TAG_PREFIX)
        origin = System.getProperty("gitFlowConfig.origin", DEFAULT_ORIGIN)
    }
}
