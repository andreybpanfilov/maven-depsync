package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSyncMojo extends AbstractMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    protected final org.apache.maven.model.Dependency coordinate = new org.apache.maven.model.Dependency();

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    @Component
    protected ArtifactResolver artifactResolver;

    @Component
    protected RepositoryArtifactChecker repositoryArtifactChecker;

    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

    @Component
    protected ModelBuilder modelBuilder;

    /**
     * Map that contains the layouts.
     */
    @Component(role = ArtifactRepositoryLayout.class)
    protected Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     * The repository system.
     */
    @Component
    protected RepositorySystem repositorySystem;

    @Component
    protected org.eclipse.aether.RepositorySystem repoSystem;

    @Component
    protected ArtifactDescriptorReader artifactDescriptorReader;

    /**
     *
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    protected List<ArtifactRepository> pomRemoteRepositories;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter(property = "sourceRepositories", required = false)
    protected String sourceRepositories;

    /**
     * Repository in the format id::[layout]::url or just url
     * myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter(property = "targetRepository", required = true)
    protected String targetRepository;

    @Parameter(defaultValue = "false", property = "syncSources")
    protected boolean syncSources;

    @Parameter(defaultValue = "false", property = "syncJavadoc")
    protected boolean syncJavadoc;

    /**
     * Synchronize transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter(property = "transitive", defaultValue = "true")
    protected boolean transitive = true;

    @Parameter(property = "scope", defaultValue = "compile")
    protected String scope = "compile";

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter(property = "artifact")
    protected String artifact;

    /**
     * The groupId of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "groupId")
    protected String groupId;

    /**
     * The artifactId of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "artifactId")
    protected String artifactId;

    /**
     * The version of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "version")
    protected String version;

    /**
     * The classifier of the artifact to sync. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "classifier")
    protected String classifier;

    @Parameter(property = "dryRun", defaultValue = "false")
    protected boolean dryRun = false;

    @Parameter(property = "useSettingsRepositories", defaultValue = "false")
    protected boolean useSettingsRepositories = false;

    protected List<RemoteRepository> source;

    protected RemoteRepository target;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (coordinate.getArtifactId() == null && artifact == null) {
            throw new MojoFailureException("You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0");
        }
        if (artifact != null) {
            String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length < 3 || tokens.length > 5) {
                throw new MojoFailureException("Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            coordinate.setGroupId(tokens[0]);
            coordinate.setArtifactId(tokens[1]);
            coordinate.setVersion(tokens[2]);
            if (tokens.length >= 4) {
                coordinate.setType(tokens[3]);
            }
            if (tokens.length == 5) {
                coordinate.setClassifier(tokens[4]);
            }
        }

        Log log = getLog();

        try {
            log.info("Processing " + coordinate);
            log.info("Source repositories: " + getSourceRepositories());
            log.info("Target repository: " + getTargetRepository());
            List<Artifact> discovered = getExistingArtifacts();
            log.info("Discovered " + discovered.size() + " artifacts");
            for (Artifact artifact : discovered) {
                log.info("\t" + artifact);
            }

            if (discovered.isEmpty()) {
                log.info("Nothing to sync, exiting");
                return;
            }

            List<Artifact> missing = getMissingArtifacts(discovered);
            log.info("Found " + missing.size() + " missing artifacts");
            for (Artifact artifact : missing) {
                log.info("\t" + artifact);
            }

            if (missing.isEmpty()) {
                log.info("Nothing to sync, exiting");
                return;
            }

            if (dryRun) {
                log.info("dry run, exiting");
                return;
            }

            log.info("Downloading missing artifacts");
            List<ArtifactResult> result = resolveMissingArtifacts(missing);

            log.info("Deploying missing artifacts");
            repoSystem.deploy(session.getRepositorySession(), createDeployRequest(result));

        } catch (DeploymentException ex) {
            throw new MojoExecutionException("Couldn't deploy artifacts", ex);
        }
    }

    protected abstract List<Artifact> getExistingArtifacts() throws MojoFailureException, MojoExecutionException;

    protected List<RemoteRepository> getRemoteRepositories(List<ArtifactRepository> remoteRepositories, boolean injectMirror) {
        Settings settings = this.session.getSettings();
        if (injectMirror) {
            repositorySystem.injectMirror(remoteRepositories, settings.getMirrors());
        }
        repositorySystem.injectProxy(remoteRepositories, settings.getProxies());
        repositorySystem.injectAuthentication(remoteRepositories, settings.getServers());
        return RepositoryUtils.toRepos(remoteRepositories);
    }

    protected List<Artifact> addClassifiersAndPoms(List<Artifact> artifacts) {
        List<Artifact> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Artifact artifact : artifacts) {
            if (seen.add(getId(artifact))) {
                result.add(artifact);
            }
            Artifact pomArtifact = ArtifactDescriptorUtils.toPomArtifact(artifact);
            if (seen.add(getId(pomArtifact))) {
                result.add(pomArtifact);
            }
            if (syncJavadoc) {
                Artifact classifiedArtifact = withClassifier(artifact, "javadoc");
                if (seen.add(getId(classifiedArtifact))) {
                    result.add(classifiedArtifact);
                }
            }
            if (syncSources) {
                Artifact classifiedArtifact = withClassifier(artifact, "sources");
                if (seen.add(getId(classifiedArtifact))) {
                    result.add(classifiedArtifact);
                }
            }
        }
        return result;
    }

    protected List<Artifact> getMissingArtifacts(List<Artifact> requiredArtifacts) throws MojoFailureException, MojoExecutionException {
        try {
            RepositorySystemSession repositorySession = session.getRepositorySession();
            List<ArtifactRequest> requests = artifactRequests(requiredArtifacts, Collections.singletonList(getTargetRepository()));
            List<ArtifactResult> target = repositoryArtifactChecker.checkArtifacts(repositorySession, requests);

            List<Artifact> missing = new ArrayList<>();
            for (ArtifactResult result : target) {
                checkResult(result, ArtifactNotFoundException.class::isInstance);
                if (result.getArtifact() == null) {
                    missing.add(result.getRequest().getArtifact());
                }
            }
            missing.sort(Comparator.comparing(Artifact::toString));
            return missing;
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException("Failed to get missing artifacts", ex);
        }
    }

    protected List<Artifact> collectDependencies(CollectRequest collectRequest) throws MojoExecutionException {
        try {
            CollectResult collectResult = repoSystem.collectDependencies(session.getRepositorySession(), collectRequest);
            return extractArtifacts(collectResult);
        } catch (DependencyCollectionException ex) {
            throw new MojoExecutionException("Failed to collect dependencies", ex);
        }
    }

    protected List<ArtifactResult> resolveMissingArtifacts(List<Artifact> artifacts) throws MojoFailureException, MojoExecutionException {
        try {
            List<ArtifactRequest> requests = artifactRequests(artifacts, getSourceRepositories());
            List<ArtifactResult> downloaded = artifactResolver.resolveArtifacts(session.getRepositorySession(), requests);
            for (ArtifactResult result : downloaded) {
                checkResult(result, e -> false);
            }
            return downloaded;
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve artifacts", ex);
        }
    }

    protected List<Artifact> getExistingArtifacts(List<Artifact> artifacts) throws MojoFailureException, MojoExecutionException {
        try {
            RepositorySystemSession repositorySession = session.getRepositorySession();
            List<ArtifactRequest> requests = artifactRequests(artifacts, getSourceRepositories());
            List<ArtifactResult> sourceArtifacts = repositoryArtifactChecker.checkArtifacts(repositorySession, requests);
            List<Artifact> discovered = new ArrayList<>();
            for (ArtifactResult result : sourceArtifacts) {
                checkResult(result, ArtifactNotFoundException.class::isInstance);
                if (result.getArtifact() != null) {
                    discovered.add(result.getArtifact());
                }
            }
            discovered.sort(Comparator.comparing(Artifact::toString));
            return discovered;
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve artifacts", ex);
        }
    }

    protected void checkResult(ArtifactResult result, Predicate<Exception> ignore) throws MojoExecutionException {
        List<Exception> exceptions = result.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (Exception exception : exceptions) {
            if (!ignore.test(exception)) {
                throw new MojoExecutionException("Failed to resolve artifact " + result.getRequest().getArtifact(), exceptions.get(0));
            }
        }
    }

    protected void checkResult(ArtifactDescriptorResult result, Predicate<Exception> ignore) throws MojoExecutionException {
        List<Exception> exceptions = result.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        for (Exception exception : exceptions) {
            if (!ignore.test(exception)) {
                throw new MojoExecutionException("Failed to load descriptor " + result.getRequest().getArtifact(), exceptions.get(0));
            }
        }
    }

    protected DeployRequest createDeployRequest(List<ArtifactResult> artifacts) throws MojoFailureException, MojoExecutionException {
        DeployRequest deployRequest = new DeployRequest();
        deployRequest.setRepository(getTargetRepository());
        for (ArtifactResult artifactResult : artifacts) {
            Artifact artifact = artifactResult.getArtifact();
            deployRequest.addArtifact(artifact);
        }
        return deployRequest;
    }

    protected ArtifactRepository parseRepository(String repo, ArtifactRepositoryPolicy policy) throws MojoFailureException {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout("default");
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if (repo.contains("::")) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);
            if (!matcher.matches()) {
                throw new MojoFailureException(repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\".");
            }

            id = matcher.group(1).trim();
            if (!StringUtils.isEmpty(matcher.group(2))) {
                layout = getLayout(matcher.group(2).trim());
            }
            url = matcher.group(3).trim();
        }
        return new MavenArtifactRepository(id, url, layout, policy, policy);
    }

    protected ArtifactRepositoryLayout getLayout(String id) throws MojoFailureException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);
        if (layout == null) {
            throw new MojoFailureException(id, "Invalid repository layout", "Invalid repository layout: " + id);
        }
        return layout;
    }

    protected RemoteRepository getTargetRepository() throws MojoFailureException {
        if (target == null) {
            ArtifactRepositoryPolicy updateAlways = getUpdateAlwaysPolicy();
            ArtifactRepository repository = parseRepository(targetRepository, updateAlways);
            target = getRemoteRepositories(Collections.singletonList(repository), false).get(0);
        }
        return target;
    }

    protected List<RemoteRepository> getSourceRepositories() throws MojoFailureException {
        if (source == null) {
            ArtifactRepositoryPolicy updateAlways = getUpdateAlwaysPolicy();
            List<ArtifactRepository> repositories = new ArrayList<>();
            if (sourceRepositories != null) {
                String[] repos = StringUtils.split(sourceRepositories, ",");
                for (String repo : repos) {
                    repositories.add(parseRepository(repo, updateAlways));
                }
            }
            if (pomRemoteRepositories != null && useSettingsRepositories) {
                repositories.addAll(pomRemoteRepositories);
            }

            if (repositories.isEmpty()) {
                throw new MojoFailureException("No source repositories set, "
                        + "specify -DsourceRepositories=id::layout::url");
            }

            source = getRemoteRepositories(repositories, true);
        }
        return source;
    }

    private ArtifactRepositoryPolicy getUpdateAlwaysPolicy() {
        return new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);
    }

    protected List<Artifact> extractArtifacts(CollectResult collectResult) {
        CollectAllDependenciesVisitor visitor = new CollectAllDependenciesVisitor();
        collectResult.getRoot().accept(visitor);
        List<Artifact> artifacts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Artifact artifact : visitor.getArtifacts()) {
            if (seen.add(getId(artifact))) {
                artifacts.add(artifact);
            }
        }
        Artifact rootArtifact = collectResult.getRoot().getArtifact();
        if (seen.add(getId(rootArtifact))) {
            artifacts.add(rootArtifact);
        }
        return artifacts;
    }

    protected ArtifactRequest artifactRequest(Artifact aetherArtifact, List<RemoteRepository> remoteRepositories) {
        return new ArtifactRequest(aetherArtifact, remoteRepositories, null);
    }

    protected List<ArtifactRequest> artifactRequests(List<Artifact> aetherArtifacts, List<RemoteRepository> remoteRepositories) {
        List<ArtifactRequest> result = new ArrayList<>();
        for (Artifact artifact : aetherArtifacts) {
            result.add(artifactRequest(artifact, remoteRepositories));
        }
        return result;
    }

    protected Artifact getArtifact(Dependency coordinate) {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(coordinate.getType());
        return new DefaultArtifact(
                coordinate.getGroupId(),
                coordinate.getArtifactId(),
                coordinate.getClassifier(),
                artifactHandler.getExtension(),
                coordinate.getVersion()
        );
    }

    protected Artifact withClassifier(Artifact artifact, String classifier) {
        return new DefaultArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                classifier,
                artifact.getExtension(),
                artifact.getVersion()
        );
    }

    protected static String getId(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getClassifier() + ':' + artifact.getExtension();
    }


    /**
     * @param groupId The groupId.
     */
    public void setGroupId(String groupId) {
        this.coordinate.setGroupId(groupId);
    }

    /**
     * @param artifactId The artifactId.
     */
    public void setArtifactId(String artifactId) {
        this.coordinate.setArtifactId(artifactId);
    }

    /**
     * @param version The version.
     */
    public void setVersion(String version) {
        this.coordinate.setVersion(version);
    }

    /**
     * @param classifier The classifier to be used.
     */
    public void setClassifier(String classifier) {
        this.coordinate.setClassifier(classifier);
    }

    /**
     * @param type packaging.
     */
    public void setPackaging(String type) {
        this.coordinate.setType(type);
    }


}
