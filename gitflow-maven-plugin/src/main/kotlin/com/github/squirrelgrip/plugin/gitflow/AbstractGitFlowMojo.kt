package com.github.squirrelgrip.plugin.gitflow

import com.github.squirrelgrip.plugin.gitflow.prompter.GitFlowPrompter
import org.apache.maven.artifact.ArtifactUtils
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.settings.Settings
import org.apache.maven.shared.release.policy.version.VersionPolicy
import org.codehaus.plexus.util.FileUtils
import org.codehaus.plexus.util.Os
import org.codehaus.plexus.util.StringUtils
import org.codehaus.plexus.util.cli.CommandLineException
import org.codehaus.plexus.util.cli.CommandLineUtils
import org.codehaus.plexus.util.cli.Commandline
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

abstract class AbstractGitFlowMojo : AbstractMojo() {
    /** Command line for Git executable.  */
    private val cmdGit = Commandline()

    /** Command line for Maven executable.  */
    private val cmdMvn = Commandline()

    private val gitModulesExists: Boolean = FileUtils.fileExists(".gitmodules")

    /** Git flow configuration.  */
    @Parameter(defaultValue = "\${gitFlowConfig}")
    protected var gitFlowConfig: GitFlowConfig = GitFlowConfig()

    /**
     * Git commit messages.
     *
     * @since 1.2.1
     */
    @Parameter(defaultValue = "\${commitMessages}")
    protected var commitMessages: CommitMessages = CommitMessages()

    /**
     * Whether this is Tycho build.
     *
     * @since 1.1.0
     */
    @Parameter(defaultValue = "false")
    protected var tychoBuild = false

    /**
     * Whether to call Maven install goal during the mojo execution.
     *
     * @since 1.0.5
     */
    @Parameter(property = "installProject", defaultValue = "false")
    protected var installProject = false

    /**
     * Whether to fetch remote branch and compare it with the local one.
     *
     * @since 1.3.0
     */
    @Parameter(property = "fetchRemote", defaultValue = "true")
    protected var fetchRemote = false

    /**
     * Whether to print commands output into the console.
     *
     * @since 1.0.7
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private val verbose = false

    /**
     * Command line arguments to pass to the underlying Maven commands.
     *
     * @since 1.8.0
     */
    @Parameter(property = "argLine")
    private var argLine: String? = null

    /**
     * Whether to make a GPG-signed commit.
     *
     * @since 1.9.0
     */
    @Parameter(property = "gpgSignCommit", defaultValue = "false")
    private val gpgSignCommit = false

    /**
     * Whether to set -DgroupId='*' -DartifactId='*' when calling
     * versions-maven-plugin.
     *
     * @since 1.10.0
     */
    @Parameter(property = "versionsForceUpdate", defaultValue = "false")
    private val versionsForceUpdate = false

    /**
     * Property to set version to.
     *
     * @since 1.13.0
     */
    @Parameter(property = "versionProperty")
    private val versionProperty: String? = null

    /**
     * Whether to skip updating version. Useful with [.versionProperty] to be
     * able to update `revision` property without modifying version tag.
     *
     * @since 1.13.0
     */
    @Parameter(property = "skipUpdateVersion")
    private val skipUpdateVersion = false

    /**
     * Prefix that is applied to commit messages.
     *
     * @since 1.14.0
     */
    @Parameter(property = "commitMessagePrefix")
    private val commitMessagePrefix: String? = null

    /**
     * Whether to update the `project.build.outputTimestamp` property
     * automatically or not.
     *
     * @since 1.17.0
     */
    @Parameter(property = "updateOutputTimestamp", defaultValue = "true")
    private val updateOutputTimestamp = true

    /**
     * The role-hint for the
     * [org.apache.maven.shared.release.policy.version.VersionPolicy]
     * implementation used to calculate the project versions. If a policy is set
     * other parameters controlling the generation of version are ignored
     * (digitsOnlyDevVersion, versionDigitToIncrement).
     *
     * @since 1.18.0
     */
    @Parameter(property = "projectVersionPolicyId")
    private val projectVersionPolicyId: String? = null

    /**
     * Version of versions-maven-plugin to use.
     *
     * @since 1.18.0
     */
    @Parameter(property = "versionsMavenPluginVersion", defaultValue = "2.16.0")
    private val versionsMavenPluginVersion = "2.16.0"

    /**
     * Version of tycho-versions-plugin to use.
     *
     * @since 1.18.0
     */
    @Parameter(property = "tychoVersionsPluginVersion", defaultValue = "1.7.0")
    private val tychoVersionsPluginVersion = "1.7.0"

    /**
     * Options to pass to Git push command using `--push-option`.
     * Multiple options can be added separated with a space e.g.
     * `-DgitPushOptions="merge_request.create merge_request.target=develop
     * merge_request.label='Super feature'"`
     *
     * @since 1.18.0
     */
    @Parameter(property = "gitPushOptions")
    private val gitPushOptions: String? = null

    /**
     * Explicitly enable or disable executing Git submodule update before commit. By
     * default plugin tries to automatically determine if update of the Git
     * submodules is needed.
     *
     * @since 1.19.0
     */
    @Parameter(property = "updateGitSubmodules")
    private val updateGitSubmodules: Boolean? = null

    /**
     * The path to the Maven executable. Defaults to "mvn".
     */
    @Parameter(property = "mvnExecutable")
    private var mvnExecutable: String? = null

    /**
     * The path to the Git executable. Defaults to "git".
     */
    @Parameter(property = "gitExecutable")
    private var gitExecutable: String? = null

    @Parameter(defaultValue = "\${session}", readonly = true)
    protected var mavenSession: MavenSession? = null

    @Component
    protected var projectBuilder: ProjectBuilder? = null

    @Component
    protected var prompter: GitFlowPrompter? = null

    /** Maven settings.  */
    @Parameter(defaultValue = "\${settings}", readonly = true)
    protected var settings: Settings? = null

    @Component
    protected var versionPolicies: Map<String?, VersionPolicy>? = null

    /**
     * Initializes command line executables.
     */
    private fun initExecutables() {
        if (StringUtils.isBlank(cmdMvn.executable)) {
            if (StringUtils.isBlank(mvnExecutable)) {
                val javaCommand = mavenSession!!.systemProperties.getProperty("sun.java.command", "")
                val wrapper = javaCommand.startsWith("org.apache.maven.wrapper.MavenWrapperMain")
                mvnExecutable = if (wrapper) {
                    "." + File.separator + "mvnw"
                } else {
                    "mvn"
                }
            }
            cmdMvn.executable = mvnExecutable
        }
        if (StringUtils.isBlank(cmdGit.executable)) {
            if (StringUtils.isBlank(gitExecutable)) {
                gitExecutable = "git"
            }
            cmdGit.executable = gitExecutable
        }
    }

    /**
     * Validates plugin configuration. Throws exception if configuration is not
     * valid.
     *
     * @param params
     * Configuration parameters to validate.
     */
    protected fun validateConfiguration(vararg params: String) {
        if (StringUtils.isNotBlank(argLine) && MAVEN_DISALLOWED_PATTERN.matcher(argLine).find()) {
            throw MojoFailureException("The argLine doesn't match allowed pattern.")
        }
        if (params.isNotEmpty()) {
            for (p in params) {
                if (StringUtils.isNotBlank(p) && MAVEN_DISALLOWED_PATTERN.matcher(p).find()) {
                    throw MojoFailureException("The '$p' value doesn't match allowed pattern.")
                }
            }
        }
    }

    protected val currentProjectVersion: String
        /**
         * Gets current project version from pom.xml file.
         *
         * @return Current project version.
         * @throws MojoFailureException
         * If current project version cannot be obtained.
         */
        get() {
            val reloadedProject = reloadProject(mavenSession!!.currentProject)
            if (reloadedProject.version == null) {
                throw MojoFailureException(
                    "Cannot get current project version. This plugin should be executed from the parent project."
                )
            }
            return reloadedProject.version
        }

    private val currentProjectOutputTimestamp: String
        /**
         * Gets current project [.REPRODUCIBLE_BUILDS_PROPERTY] property value
         * from pom.xml file.
         *
         * @return Value of [.REPRODUCIBLE_BUILDS_PROPERTY] property.
         * @throws MojoFailureException
         * If project loading fails.
         */
        get() {
            val reloadedProject = reloadProject(mavenSession!!.currentProject)
            return reloadedProject.properties.getProperty(REPRODUCIBLE_BUILDS_PROPERTY)
        }

    /**
     * Reloads projects info from file.
     *
     * @param project
     * @return Reloaded Maven projects.
     */
    private fun reloadProjects(project: MavenProject): List<MavenProject> {
        return try {
            val result = projectBuilder!!.build(
                listOf(project.file),
                true,
                mavenSession!!.projectBuildingRequest
            )
            val projects: MutableList<MavenProject> = ArrayList()
            for (projectBuildingResult in result) {
                projects.add(projectBuildingResult.project)
            }
            projects
        } catch (e: Exception) {
            throw MojoFailureException("Error re-loading project info", e)
        }
    }

    /**
     * Reloads project info from file.
     *
     * @param project
     * @return Maven project which is the execution root.
     */
    private fun reloadProject(project: MavenProject): MavenProject {
        val projects = reloadProjects(project)
        for (resultProject in projects) {
            if (resultProject.isExecutionRoot) {
                return resultProject
            }
        }
        throw NoSuchElementException(
            "No reloaded project appears to be the execution root (" + project.groupId + ":" + project.artifactId + ")"
        )
    }

    /**
     * Compares the production branch name with the development branch name.
     *
     * @return `true` if the production branch name is different from
     * the development branch name, `false` otherwise.
     */
    protected fun notSameProdDevName(): Boolean {
        return gitFlowConfig!!.productionBranch != gitFlowConfig!!.developmentBranch
    }

    /**
     * Checks uncommitted changes.
     */
    protected fun checkUncommittedChanges() {
        log.info("Checking for uncommitted changes.")
        if (executeGitHasUncommitted()) {
            throw MojoFailureException("You have some uncommitted files. Commit or discard local changes in order to proceed.")
        }
    }

    protected fun checkSnapshotDependencies() {
        log.info("Checking for SNAPSHOT versions in dependencies.")
        val snapshots: MutableList<String> = ArrayList()
        val builtArtifacts: MutableSet<String> = HashSet()
        val projects = reloadProjects(mavenSession!!.currentProject)
        for (project in projects) {
            builtArtifacts.add(project.groupId + ":" + project.artifactId + ":" + project.version)
        }
        for (project in projects) {
            val dependencies = project.dependencies
            for (d in dependencies) {
                val id = d.groupId + ":" + d.artifactId + ":" + d.version
                if (!builtArtifacts.contains(id) && ArtifactUtils.isSnapshot(d.version)) {
                    snapshots.add("$project -> $d")
                }
            }
            val parent = project.parent
            if (parent != null) {
                val id = parent.groupId + ":" + parent.artifactId + ":" + parent.version
                if (!builtArtifacts.contains(id) && ArtifactUtils.isSnapshot(parent.version)) {
                    snapshots.add("$project -> $parent")
                }
            }
        }
        if (!snapshots.isEmpty()) {
            for (s in snapshots) {
                log.warn(s)
            }
            throw MojoFailureException(
                "There is some SNAPSHOT dependencies in the project, see warnings above."
                    + " Change them or ignore with `allowSnapshots` property."
            )
        }
    }

    /**
     * Checks if branch name is acceptable.
     *
     * @param branchName
     * Branch name to check.
     * @return `true` when name is valid, `false` otherwise.
     */
    fun validBranchName(branchName: String?): Boolean =
        executeGitCommandExitCode("check-ref-format", "--allow-onelevel", branchName!!).isSuccess

    /**
     * Checks if version is valid.
     *
     * @param version
     * Version to validate.
     * @return `true` when version is valid, `false`
     * otherwise.
     */
    protected fun validVersion(version: String): Boolean {
        val valid = "" == version || GitFlowVersionInfo.isValidVersion(version) && validBranchName(version)
        if (!valid) {
            log.info("The version is not valid.")
        }
        return valid
    }

    /**
     * Executes git commands to check for uncommitted changes.
     *
     * @return `true` when there are uncommitted changes,
     * `false` otherwise.
     */
    private fun executeGitHasUncommitted(): Boolean {
        var uncommited = false

        // 1 if there were differences and 0 means no differences

        // git diff --no-ext-diff --ignore-submodules --quiet --exit-code
        val diffCommandResult = executeGitCommandExitCode(
            "diff", "--no-ext-diff", "--ignore-submodules", "--quiet", "--exit-code"
        )
        var error: String? = null
        if (diffCommandResult.isSuccess) {
            // git diff-index --cached --quiet --ignore-submodules HEAD --
            val diffIndexCommandResult = executeGitCommandExitCode(
                "diff-index", "--cached", "--quiet", "--ignore-submodules", "HEAD", "--"
            )
            if (!diffIndexCommandResult.isSuccess) {
                error = diffIndexCommandResult.error
                uncommited = true
            }
        } else {
            error = diffCommandResult.error
            uncommited = true
        }
        if (StringUtils.isNotBlank(error)) {
            throw MojoFailureException(error)
        }
        return uncommited
    }

    /**
     * Executes git config commands to set Git Flow configuration.
     */
    protected fun initGitFlowConfig() {
        gitSetConfig("gitflow.branch.master", gitFlowConfig!!.productionBranch)
        gitSetConfig("gitflow.branch.develop", gitFlowConfig!!.developmentBranch)
        gitSetConfig("gitflow.prefix.feature", gitFlowConfig!!.featureBranchPrefix)
        gitSetConfig("gitflow.prefix.release", gitFlowConfig!!.releaseBranchPrefix)
        gitSetConfig("gitflow.prefix.hotfix", gitFlowConfig!!.hotfixBranchPrefix)
        gitSetConfig("gitflow.prefix.support", gitFlowConfig!!.supportBranchPrefix)
        gitSetConfig("gitflow.prefix.versiontag", gitFlowConfig!!.versionTagPrefix)
        gitSetConfig("gitflow.origin", gitFlowConfig!!.origin)
    }

    /**
     * Executes git config command.
     *
     * @param name
     * Option name.
     * @param value
     * Option value.
     */
    private fun gitSetConfig(name: String, value: String) {
        var value: String? = value
        if (value == null || value.isEmpty()) {
            value = if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                "\"\""
            } else {
                ""
            }
        }

        // ignore error exit codes
        executeGitCommandExitCode("config", name, value)
    }

    /**
     * Executes git for-each-ref with `refname:short` format.
     *
     * @param branchName
     * Branch name to find.
     * @param firstMatch
     * Return first match.
     * @return Branch names which matches `refs/heads/{branchName}*`.
     */
    protected fun gitFindBranches(branchName: String, firstMatch: Boolean): String {
        return gitFindBranches("refs/heads/", branchName, firstMatch)
    }

    /**
     * Executes git for-each-ref with `refname:short` format.
     *
     * @param refs
     * Refs to search.
     * @param branchName
     * Branch name to find.
     * @param firstMatch
     * Return first match.
     * @return Branch names which matches `{refs}{branchName}*`.
     */
    private fun gitFindBranches(refs: String, branchName: String, firstMatch: Boolean): String {
        var wildcard = "*"
        if (branchName.endsWith("/")) {
            wildcard = "**"
        }
        var branches: String
        branches = if (firstMatch) {
            executeGitCommandReturn(
                "for-each-ref", "--count=1",
                "--format=\"%(refname:short)\"", refs + branchName + wildcard
            )
        } else {
            executeGitCommandReturn(
                "for-each-ref",
                "--format=\"%(refname:short)\"", refs + branchName + wildcard
            )
        }

        // on *nix systems return values from git for-each-ref are wrapped in
        // quotes
        // https://github.com/aleksandr-m/gitflow-maven-plugin/issues/3
        branches = removeQuotes(branches)
        branches = StringUtils.strip(branches)
        return branches
    }

    /**
     * Executes git for-each-ref to get all tags.
     *
     * @return Git tags.
     */
    protected fun gitFindTags(): String =
        removeQuotes(
            executeGitCommandReturn(
                "for-each-ref",
                "--sort=*authordate",
                "--format=\"%(refname:short)\"",
                "refs/tags/"
            )
        )

    /**
     * Executes git for-each-ref to get the last tag.
     *
     * @return Last tag.
     */
    protected fun gitFindLastTag(): String =
        removeQuotes(
            executeGitCommandReturn(
                "for-each-ref", "--sort=-version:refname", "--sort=-taggerdate",
                "--count=1", "--format=\"%(refname:short)\"", "refs/tags/"
            )
        ).replace("\\r?\\n".toRegex(), "")

    /**
     * Removes double quotes from the string.
     *
     * @param str
     * String to remove quotes from.
     * @return String without quotes.
     */
    private fun removeQuotes(str: String): String =
        StringUtils.replace(str, "\"", "")

    /**
     * Gets the current branch name.
     *
     * @return Current branch name.
     */
    protected fun gitCurrentBranch(): String =
        StringUtils.strip(executeGitCommandReturn("symbolic-ref", "-q", "--short", "HEAD"))

    /**
     * Checks if local branch with given name exists.
     *
     * @param branchName
     * Name of the branch to check.
     * @return `true` if local branch exists, `false`
     * otherwise.
     */
    protected fun gitCheckBranchExists(branchName: String): Boolean =
        executeGitCommandExitCode("show-ref", "--verify", "--quiet", "refs/heads/$branchName").isSuccess

    /**
     * Checks if local tag with given name exists.
     *
     * @param tagName
     * Name of the tag to check.
     * @return `true` if local tag exists, `false` otherwise.
     */
    protected fun gitCheckTagExists(tagName: String): Boolean =
        executeGitCommandExitCode("show-ref", "--verify", "--quiet", "refs/tags/$tagName").isSuccess

    /**
     * Executes git checkout.
     *
     * @param branchName
     * Branch name to checkout.
     */
    protected fun gitCheckout(branchName: String) {
        log.info("Checking out '$branchName' branch.")
        executeGitCommand("checkout", branchName)
    }

    /**
     * Executes git checkout -b.
     *
     * @param newBranchName
     * Create branch with this name.
     * @param fromBranchName
     * Create branch from this branch.
     */
    protected fun gitCreateAndCheckout(newBranchName: String, fromBranchName: String) {
        log.info("Creating a new branch '$newBranchName' from '$fromBranchName' and checking it out.")
        executeGitCommand("checkout", "-b", newBranchName, fromBranchName)
    }

    /**
     * Executes git branch.
     *
     * @param newBranchName
     * Create branch with this name.
     * @param fromBranchName
     * Create branch from this branch.
     */
    protected fun gitCreateBranch(newBranchName: String, fromBranchName: String) {
        log.info("Creating a new branch '$newBranchName' from '$fromBranchName'.")
        executeGitCommand("branch", newBranchName, fromBranchName)
    }

    /**
     * Replaces properties in message.
     *
     * @param message
     * @param map
     * Key is a string to replace wrapped in `@{...}`. Value
     * is a string to replace with.
     * @return Message with replaced properties.
     */
    private fun replaceProperties(message: String, map: Map<String, String> = emptyMap()): String =
        map.asSequence().fold(message) { acc, (key, value) ->
            StringUtils.replace(acc, "@{$key}", value)
        }

    /**
     * Executes git commit -a -m, replacing `@{map.key}` with
     * `map.value`.
     *
     * @param message
     * Commit message.
     * @param messageProperties
     * Properties to replace in message.
     * @throws MojoFailureException
     * If command line execution returns false code.
     * @throws CommandLineException
     * If command line execution fails.
     */
    /**
     * Executes git commit -a -m.
     *
     * @param message
     * Commit message.
     */
    protected fun gitCommit(message: String, messageProperties: Map<String, String> = emptyMap()) {
        var newMessage = message
        if (gitModulesExists && updateGitSubmodules == null || java.lang.Boolean.TRUE == updateGitSubmodules) {
            log.info("Updating git submodules before commit.")
            executeGitCommand("submodule", "update")
        }
        if (StringUtils.isNotBlank(commitMessagePrefix)) {
            newMessage = commitMessagePrefix + newMessage
        }
        newMessage = replaceProperties(newMessage, messageProperties)
        if (gpgSignCommit) {
            log.info("Committing changes. GPG-signed.")
            executeGitCommand("commit", "-a", "-S", "-m", newMessage)
        } else {
            log.info("Committing changes.")
            executeGitCommand("commit", "-a", "-m", newMessage)
        }
    }

    /**
     * Executes git rebase or git merge --ff-only or git merge --no-ff or git merge.
     *
     * @param branchName
     * Branch name to merge.
     * @param rebase
     * Do rebase.
     * @param noff
     * Merge with --no-ff.
     * @param ffonly
     * Merge with --ff-only.
     * @param message
     * Merge commit message.
     * @param messageProperties
     * Properties to replace in message.
     */
    fun gitMerge(
        branchName: String,
        rebase: Boolean,
        noff: Boolean,
        ffonly: Boolean,
        message: String,
        messageProperties: Map<String, String> = emptyMap()
    ) {
        var newMessage = message
        var sign: String? = null
        if (gpgSignCommit) {
            sign = "-S"
        }
        var msgParam: String? = null
        var msg: String? = null
        if (StringUtils.isNotBlank(newMessage)) {
            if (StringUtils.isNotBlank(commitMessagePrefix)) {
                newMessage = commitMessagePrefix + newMessage
            }
            msgParam = "-m"
            msg = replaceProperties(newMessage, messageProperties)
        }
        if (rebase) {
            log.info("Rebasing '$branchName' branch.")
            executeGitCommand("rebase", sign!!, branchName)
        } else if (ffonly) {
            log.info("Merging (--ff-only) '$branchName' branch.")
            executeGitCommand("merge", "--ff-only", sign!!, branchName)
        } else if (noff) {
            log.info("Merging (--no-ff) '$branchName' branch.")
            executeGitCommand("merge", "--no-ff", sign!!, branchName, msgParam!!, msg!!)
        } else {
            log.info("Merging '$branchName' branch.")
            executeGitCommand("merge", sign!!, branchName, msgParam!!, msg!!)
        }
    }

    /**
     * Executes git merge --no-ff.
     *
     * @param branchName
     * Branch name to merge.
     * @param message
     * Merge commit message.
     * @param messageProperties
     * Properties to replace in message.
     */
    fun gitMergeNoff(
        branchName: String,
        message: String,
        messageProperties: Map<String, String> = emptyMap()
    ) {
        gitMerge(branchName, false, true, false, message, messageProperties)
    }

    /**
     * Executes git merge --squash.
     *
     * @param branchName
     * Branch name to merge.
     */
    protected fun gitMergeSquash(branchName: String) {
        log.info("Squashing '$branchName' branch.")
        executeGitCommand("merge", "--squash", branchName)
    }

    /**
     * Executes git tag -a [-s] -m.
     *
     * @param tagName
     * Name of the tag.
     * @param message
     * Tag message.
     * @param gpgSignTag
     * Make a GPG-signed tag.
     * @param messageProperties
     * Properties to replace in message.
     */
    protected fun gitTag(
        tagName: String,
        message: String,
        gpgSignTag: Boolean,
        messageProperties: Map<String, String> = emptyMap()
    ) {
        var newMessage = message
        newMessage = replaceProperties(newMessage, messageProperties)
        if (gpgSignTag) {
            log.info("Creating GPG-signed '$tagName' tag.")
            executeGitCommand("tag", "-a", "-s", tagName, "-m", newMessage)
        } else {
            log.info("Creating '$tagName' tag.")
            executeGitCommand("tag", "-a", tagName, "-m", newMessage)
        }
    }

    /**
     * Executes git branch -d.
     *
     * @param branchName
     * Branch name to delete.
     */
    protected fun gitBranchDelete(branchName: String) {
        log.info("Deleting '$branchName' branch.")
        executeGitCommand("branch", "-d", branchName)
    }

    /**
     * Executes git branch -D.
     *
     * @param branchName
     * Branch name to delete.
     */
    protected fun gitBranchDeleteForce(branchName: String) {
        log.info("Deleting (-D) '$branchName' branch.")
        executeGitCommand("branch", "-D", branchName)
    }

    /**
     * Executes git fetch and checks if local branch exists. If local branch is
     * present then compares it with the remote, if not then branch is checked out.
     *
     * @param branchName
     * Branch name to check.
     */
    protected fun gitFetchRemoteAndCompareCreate(branchName: String) {
        val remoteBranchExists = StringUtils.isNotBlank(gitFetchAndFindRemoteBranches(branchName, true))
        if (gitCheckBranchExists(branchName)) {
            if (remoteBranchExists) {
                log.info(
                    "Comparing local branch '" + branchName + "' with remote '" + gitFlowConfig!!.origin + "/" + branchName + "'."
                )
                val revlistout = executeGitCommandReturn(
                    "rev-list", "--left-right", "--count",
                    branchName + "..." + gitFlowConfig!!.origin + "/" + branchName
                )
                val counts = org.apache.commons.lang3.StringUtils.split(revlistout, '\t')
                if (counts != null && counts.size > 1 && "0" != org.apache.commons.lang3.StringUtils.deleteWhitespace(
                        counts[1]
                    )
                ) {
                    throw MojoFailureException(
                        "Remote branch '" + gitFlowConfig!!.origin + "/" + branchName
                            + "' is ahead of the local branch '" + branchName + "'. Execute git pull."
                    )
                }
            }
        } else {
            log.info("Local branch '" + branchName + "' doesn't exist. Trying check it out from '" + gitFlowConfig!!.origin + "'.")
            gitCreateAndCheckout(branchName, gitFlowConfig!!.origin + "/" + branchName)
        }
    }

    /**
     * Executes git fetch and git for-each-ref with `refname:short`
     * format. Searches `refs/remotes/{gitFlowConfig#origin}/`.
     *
     * @param branchName
     * Branch name to find.
     * @param firstMatch
     * Return first match.
     * @return Branch names which matches
     * `refs/remotes/{gitFlowConfig#origin}/{branchName}*`.
     */
    fun gitFetchAndFindRemoteBranches(branchName: String, firstMatch: Boolean): String {
        gitFetchRemote()
        return gitFindBranches("refs/remotes/" + gitFlowConfig!!.origin + "/", branchName, firstMatch)
    }

    /**
     * Executes git fetch.
     *
     * @return `true` if git fetch returned success exit code,
     * `false` otherwise.
     */
    private fun gitFetchRemote(): Boolean {
        log.info("Fetching remote from '" + gitFlowConfig!!.origin + "'.")
        val result = executeGitCommandExitCode("fetch", "--quiet", gitFlowConfig!!.origin)
        if (!result.isSuccess) {
            log.warn(
                "There were some problems fetching from '"
                    + gitFlowConfig!!.origin
                    + "'. You can turn off remote fetching by setting the 'fetchRemote' parameter to false."
            )
        }
        return result.isSuccess
    }

    /**
     * Executes git push, optionally with the `--follow-tags` argument.
     *
     * @param branchName
     * Branch name to push.
     * @param pushTags
     * If `true` adds `--follow-tags` argument to
     * the git `push` command.
     */
    protected fun gitPush(branchName: String, pushTags: Boolean) {
        log.info("Pushing '" + branchName + "' branch to '" + gitFlowConfig!!.origin + "'.")
        val args: MutableList<String> = ArrayList()
        args.add("push")
        args.add("--quiet")
        args.add("-u")
        if (pushTags) {
            args.add("--follow-tags")
        }
        if (StringUtils.isNotBlank(gitPushOptions)) {
            try {
                val opts = CommandLineUtils.translateCommandline(gitPushOptions)
                for (opt in opts) {
                    args.add("--push-option=$opt")
                }
            } catch (e: Exception) {
                throw CommandLineException(e.message, e)
            }
        }
        args.add(gitFlowConfig!!.origin)
        args.add(branchName)
        executeGitCommand(*args.toTypedArray<String>())
    }

    protected fun gitPushDelete(branchName: String) {
        log.info("Deleting remote branch '" + branchName + "' from '" + gitFlowConfig!!.origin + "'.")
        val result = executeGitCommandExitCode("push", "--delete", gitFlowConfig!!.origin, branchName)
        if (!result.isSuccess) {
            log.warn(
                "There were some problems deleting remote branch '" + branchName + "' from '" + gitFlowConfig!!.origin + "'."
            )
        }
    }

    /**
     * Executes 'set' goal of versions-maven-plugin or 'set-version' of
     * tycho-versions-plugin in case it is tycho build.
     *
     * @param version
     * New version to set.
     */
    protected fun mvnSetVersions(version: String) {
        log.info("Updating version(s) to '$version'.")
        val newVersion = "-DnewVersion=$version"
        if (tychoBuild) {
            var prop: String? = null
            if (StringUtils.isNotBlank(versionProperty)) {
                prop = "-Dproperties=$versionProperty"
                log.info("Updating property '$versionProperty' to '$version'.")
            }
            executeMvnCommand(
                "$TYCHO_VERSIONS_PLUGIN:$tychoVersionsPluginVersion:$TYCHO_VERSIONS_PLUGIN_SET_GOAL", prop!!,
                newVersion, "-Dtycho.mode=maven"
            )
        } else {
            var runCommand = false
            val args: MutableList<String> = ArrayList()
            args.add("-DgenerateBackupPoms=false")
            args.add(newVersion)
            if (!skipUpdateVersion) {
                runCommand = true
                args.add("$VERSIONS_MAVEN_PLUGIN:$versionsMavenPluginVersion:$VERSIONS_MAVEN_PLUGIN_SET_GOAL")
                if (versionsForceUpdate) {
                    args.add("-DgroupId=")
                    args.add("-DartifactId=")
                }
            }
            if (StringUtils.isNotBlank(versionProperty)) {
                runCommand = true
                log.info("Updating property '$versionProperty' to '$version'.")
                args.add("$VERSIONS_MAVEN_PLUGIN:$versionsMavenPluginVersion:$VERSIONS_MAVEN_PLUGIN_SET_PROPERTY_GOAL")
                args.add("-Dproperty=$versionProperty")
            }
            if (runCommand) {
                executeMvnCommand(*args.toTypedArray<String>())
                if (updateOutputTimestamp) {
                    var timestamp = currentProjectOutputTimestamp
                    if (timestamp.isNotEmpty()) {
                        if (StringUtils.isNumeric(timestamp)) {
                            // int representing seconds since the epoch
                            timestamp = (System.currentTimeMillis() / 1000L).toString()
                        } else {
                            // ISO-8601
                            val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                            df.timeZone = TimeZone.getTimeZone("UTC")
                            timestamp = df.format(Date())
                        }
                        log.info("Updating property '$REPRODUCIBLE_BUILDS_PROPERTY' to '$timestamp'.")
                        executeMvnCommand(
                            "$VERSIONS_MAVEN_PLUGIN:$versionsMavenPluginVersion:$VERSIONS_MAVEN_PLUGIN_SET_PROPERTY_GOAL",
                            "-DgenerateBackupPoms=false",
                            "-Dproperty=" + REPRODUCIBLE_BUILDS_PROPERTY, "-DnewVersion=$timestamp"
                        )
                    }
                }
            }
        }
    }

    /**
     * Executes mvn clean test.
     */
    protected fun mvnCleanTest() {
        log.info("Cleaning and testing the project.")
        if (tychoBuild) {
            executeMvnCommand("clean", "verify")
        } else {
            executeMvnCommand("clean", "test")
        }
    }

    /**
     * Executes mvn clean install.
     */
    protected fun mvnCleanInstall() {
        log.info("Cleaning and installing the project.")
        executeMvnCommand("clean", "install")
    }

    /**
     * Executes Maven goals.
     *
     * @param goals
     * The goals to execute.
     */
    protected fun mvnRun(goals: String) {
        log.info("Running Maven goals: $goals")
        executeMvnCommand(*CommandLineUtils.translateCommandline(goals))
    }

    /**
     * Executes Git command and returns output.
     *
     * @param args
     * Git command line arguments.
     * @return Command output.
     */
    private fun executeGitCommandReturn(vararg args: String): String {
        return executeCommand(cmdGit, true, null, *args).out
    }

    /**
     * Executes Git command without failing on non successful exit code.
     *
     * @param args
     * Git command line arguments.
     * @return Command result.
     */
    private fun executeGitCommandExitCode(vararg args: String): CommandResult =
        executeCommand(cmdGit, false, null, *args)

    /**
     * Executes Git command.
     *
     * @param args
     * Git command line arguments.
     */
    private fun executeGitCommand(vararg args: String) {
        executeCommand(cmdGit, true, null, *args)
    }

    /**
     * Executes Maven command.
     *
     * @param args
     * Maven command line arguments.
     */
    private fun executeMvnCommand(vararg args: String) {
        executeCommand(cmdMvn, true, argLine, *args)
    }

    /**
     * Executes command line.
     *
     * @param cmd
     * Command line.
     * @param failOnError
     * Whether to throw exception on NOT success exit code.
     * @param argStr
     * Command line arguments as a string.
     * @param args
     * Command line arguments.
     * @return [CommandResult] instance holding command exit code, output and
     * error if any.
     */
    private fun executeCommand(
        cmd: Commandline,
        failOnError: Boolean,
        argStr: String?,
        vararg args: String
    ): CommandResult {
        // initialize executables
        initExecutables()
        if (log.isDebugEnabled) {
            log.debug(
                cmd.executable + " " + StringUtils.join(args, " ")
                    + if (argStr == null) "" else " $argStr"
            )
        }
        cmd.clearArgs()
        cmd.addArguments(args)
        if (StringUtils.isNotBlank(argStr)) {
            cmd.createArg().setLine(argStr)
        }
        val out = StringBufferStreamConsumer(verbose)
        val err = CommandLineUtils.StringStreamConsumer()

        val exitCode = CommandLineUtils.executeCommandLine(cmd, out, err)
        var errorStr = err.output
        val outStr = out.getOutput()
        if (failOnError && exitCode != SUCCESS_EXIT_CODE) {
            // not all commands print errors to error stream
            errorStr += LS + outStr
            throw MojoFailureException("Failed cmd [" + cmd.executable + "] with args [" + cmd.arguments.contentToString() + "], bad exit code [" + exitCode + "]. Out: [" + errorStr + "]")
        }
        if (verbose && StringUtils.isNotBlank(errorStr)) {
            log.warn(errorStr)
        }
        return CommandResult(exitCode, outStr, errorStr)
    }

    private class CommandResult(
        private val exitCode: Int,
        val out: String,
        val error: String
    ) {
        val isSuccess: Boolean =
            exitCode == SUCCESS_EXIT_CODE
    }

    fun setArgLine(argLine: String) {
        this.argLine = argLine
    }

    protected val versionPolicy: VersionPolicy?
        get() = if (StringUtils.isNotBlank(projectVersionPolicyId)) {
            versionPolicies!![projectVersionPolicyId]
                ?: throw IllegalArgumentException("No implementation found for projectVersionPolicyId: $projectVersionPolicyId")
        } else null

    companion object {
        /** Group and artifact id of the versions-maven-plugin.  */
        private const val VERSIONS_MAVEN_PLUGIN = "org.codehaus.mojo:versions-maven-plugin"

        /** The versions-maven-plugin set goal.  */
        private const val VERSIONS_MAVEN_PLUGIN_SET_GOAL = "set"

        /** The versions-maven-plugin set-property goal.  */
        private const val VERSIONS_MAVEN_PLUGIN_SET_PROPERTY_GOAL = "set-property"

        /** Group and artifact id of the tycho-versions-plugin.  */
        private const val TYCHO_VERSIONS_PLUGIN = "org.eclipse.tycho:tycho-versions-plugin"

        /** The tycho-versions-plugin set-version goal.  */
        private const val TYCHO_VERSIONS_PLUGIN_SET_GOAL = "set-version"

        /** Name of the property needed to have reproducible builds.  */
        private const val REPRODUCIBLE_BUILDS_PROPERTY = "project.build.outputTimestamp"

        /** System line separator.  */
        protected val LS = System.getProperty("line.separator")

        /** Success exit code.  */
        private const val SUCCESS_EXIT_CODE = 0

        /** Pattern of disallowed characters in Maven commands.  */
        private val MAVEN_DISALLOWED_PATTERN = Pattern.compile("[&|;]")
    }
}
