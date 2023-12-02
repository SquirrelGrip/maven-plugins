package com.github.squirrelgrip.plugin.gitflow

class CommitMessages {
     var featureStartMessage: String
    var featureFinishMessage: String
    var hotfixStartMessage: String
    var hotfixFinishMessage: String
    var hotfixVersionUpdateMessage: String
    var releaseStartMessage: String
    var releaseFinishMessage: String
    var releaseVersionUpdateMessage: String
    var releaseFinishMergeMessage: String
    var releaseFinishDevMergeMessage: String
    var featureFinishDevMergeMessage: String
    var featureSquashMessage: String
    var hotfixFinishMergeMessage: String
    var hotfixFinishDevMergeMessage: String
    var hotfixFinishReleaseMergeMessage: String
    var hotfixFinishSupportMergeMessage: String
    var tagHotfixMessage: String
    var tagReleaseMessage: String
    var tagVersionUpdateMessage: String
    var updateDevToAvoidConflictsMessage: String
    var updateDevBackPreMergeStateMessage: String
    var updateReleaseToAvoidConflictsMessage: String
    var updateReleaseBackPreMergeStateMessage: String
    var updateFeatureBackMessage: String
    var featureFinishIncrementVersionMessage: String
    var supportStartMessage: String
    var versionUpdateMessage: String

    init {
        featureStartMessage = System.getProperty("commitMessages.featureStartMessage", "Update versions for feature branch")
        featureFinishMessage = System.getProperty("commitMessages.featureFinishMessage", "Update versions for development branch")
        hotfixStartMessage = System.getProperty("commitMessages.hotfixStartMessage", "Update versions for hotfix")
        hotfixFinishMessage = System.getProperty("commitMessages.hotfixFinishMessage", "Update for next development version")
        hotfixVersionUpdateMessage = System.getProperty("commitMessages.hotfixVersionUpdateMessage", "Update to hotfix version")
        releaseStartMessage = System.getProperty("commitMessages.releaseStartMessage", "Update versions for release")
        releaseFinishMessage = System.getProperty("commitMessages.releaseFinishMessage", "Update for next development version")
        releaseVersionUpdateMessage = System.getProperty("commitMessages.releaseVersionUpdateMessage", "Update for next development version")
        releaseFinishMergeMessage = System.getProperty("commitMessages.releaseFinishMergeMessage", "")
        releaseFinishDevMergeMessage = System.getProperty("commitMessages.releaseFinishDevMergeMessage", "")
        featureFinishDevMergeMessage = System.getProperty("commitMessages.featureFinishDevMergeMessage", "")
        featureSquashMessage = System.getProperty("commitMessages.featureSquashMessage", "")
        hotfixFinishMergeMessage = System.getProperty("commitMessages.hotfixFinishMergeMessage", "")
        hotfixFinishDevMergeMessage = System.getProperty("commitMessages.hotfixFinishDevMergeMessage", "")
        hotfixFinishReleaseMergeMessage = System.getProperty("commitMessages.hotfixFinishReleaseMergeMessage", "")
        hotfixFinishSupportMergeMessage = System.getProperty("commitMessages.hotfixFinishSupportMergeMessage", "")
        tagHotfixMessage = System.getProperty("commitMessages.tagHotfixMessage", "Tag hotfix")
        tagReleaseMessage = System.getProperty("commitMessages.tagReleaseMessage", "Tag release")
        tagVersionUpdateMessage = System.getProperty("commitMessages.tagVersionUpdateMessage", "Tag version update")
        updateDevToAvoidConflictsMessage = System.getProperty("commitMessages.updateDevToAvoidConflictsMessage", "Update develop to production version to avoid merge conflicts")
        updateDevBackPreMergeStateMessage = System.getProperty("commitMessages.updateDevBackPreMergeStateMessage", "Update develop version back to pre-merge state")
        updateReleaseToAvoidConflictsMessage = System.getProperty("commitMessages.updateReleaseToAvoidConflictsMessage", "Update release to hotfix version to avoid merge conflicts")
        updateReleaseBackPreMergeStateMessage = System.getProperty("commitMessages.updateReleaseBackPreMergeStateMessage", "Update release version back to pre-merge state")
        updateFeatureBackMessage = System.getProperty("commitMessages.updateFeatureBackMessage", "Update feature branch back to feature version")
        featureFinishIncrementVersionMessage = System.getProperty("commitMessages.featureFinishIncrementVersionMessage", "Increment feature version")
        supportStartMessage = System.getProperty("commitMessages.supportStartMessage", "Update versions for support branch")
        versionUpdateMessage = System.getProperty("commitMessages.versionUpdateMessage", "Update versions")
    }
}
