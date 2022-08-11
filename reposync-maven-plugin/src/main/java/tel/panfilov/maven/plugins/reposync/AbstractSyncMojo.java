package tel.panfilov.maven.plugins.reposync;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import tel.panfilov.maven.plugins.reposync.component.DependencyCollector;
import tel.panfilov.maven.plugins.reposync.component.ModelAwareArtifactDescriptorReader;
import tel.panfilov.maven.plugins.reposync.component.RepositoryArtifactChecker;
import tel.panfilov.maven.plugins.reposync.component.ScopeMediator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractSyncMojo extends AbstractMojo {

    protected static final String DEFAULT_SCOPE = "compile+runtime";
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    @Component
    protected ArtifactResolver artifactResolver;

    @Component
    protected RepositoryArtifactChecker repositoryArtifactChecker;

    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

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

    @Component
    protected DependencyCollector dependencyCollector;

    @Component
    protected ScopeMediator scopeMediator;

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
     * myrepo::::https://repo.acme.com|https://repo.acme2.com
     */
    @Parameter(property = "targetRepository", required = true)
    protected String targetRepository;

    /**
     * Synchronize sources
     */
    @Parameter(defaultValue = "false", property = "syncSources")
    protected boolean syncSources;

    /**
     * Synchronize javadocs
     */
    @Parameter(defaultValue = "false", property = "syncJavadoc")
    protected boolean syncJavadoc;

    /**
     * Synchronize transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter(property = "transitive", defaultValue = "true")
    protected boolean transitive = true;

    /**
     * Option can be used to obtain a summary of what will be transferred
     */
    @Parameter(property = "dryRun", defaultValue = "false")
    protected boolean dryRun = false;

    /**
     * Load information about source repositories from settings.xml file
     */
    @Parameter(property = "useSettingsRepositories", defaultValue = "false")
    protected boolean useSettingsRepositories = false;

    protected List<RemoteRepository> source;

    protected RemoteRepository target;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        try {
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
                log.info("Dry run, exiting");
                return;
            }

            log.info("Downloading missing artifacts");
            List<ArtifactResult> result = downloadMissingArtifacts(missing);

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

    protected boolean include(Dependency dependency) {
        return true;
    }

    protected List<Artifact> addClassifiersAndPoms(List<Artifact> artifacts) {
        List<Artifact> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Artifact artifact : artifacts) {
            if (seen.add(Utils.getId(artifact))) {
                result.add(artifact);
            }
            Artifact pomArtifact = ArtifactDescriptorUtils.toPomArtifact(artifact);
            if (seen.add(Utils.getId(pomArtifact))) {
                result.add(pomArtifact);
            }
            if (Utils.isWar(artifact)) {
                Artifact classifierArtifact = Utils.toClassesArtifact(artifact);
                if (seen.add(Utils.getId(classifierArtifact))) {
                    result.add(classifierArtifact);
                }
            }
            if (Utils.isPom(artifact)) {
                continue;
            }
            if (syncJavadoc) {
                Artifact classifierArtifact = Utils.toJavadocArtifact(artifact);
                if (seen.add(Utils.getId(classifierArtifact))) {
                    result.add(classifierArtifact);
                }
            }
            if (syncSources) {
                Artifact classifierArtifact = Utils.toSourcesArtifact(artifact);
                if (seen.add(Utils.getId(classifierArtifact))) {
                    result.add(classifierArtifact);
                }
            }
        }
        return result;
    }

    protected List<Artifact> getMissingArtifacts(List<Artifact> requiredArtifacts) throws MojoFailureException, MojoExecutionException {
        try {
            RepositorySystemSession repositorySession = session.getRepositorySession();
            List<ArtifactRequest> requests = Utils.artifactRequests(requiredArtifacts, Collections.singletonList(getTargetRepository()));
            List<ArtifactResult> target = repositoryArtifactChecker.checkArtifacts(repositorySession, false, requests);

            List<Artifact> missing = new ArrayList<>();
            for (ArtifactResult result : target) {
                Utils.checkResult(result, ArtifactNotFoundException.class::isInstance);
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

    protected List<Artifact> collectDependencies(CollectRequest collectRequest, int depth, String scope) throws MojoExecutionException {
        try {
            return dependencyCollector.collectDependencies(session.getRepositorySession(), collectRequest, depth, scope);
        } catch (DependencyCollectionException ex) {
            throw new MojoExecutionException("Failed to collect dependencies", ex);
        }
    }

    protected List<ArtifactResult> downloadMissingArtifacts(List<Artifact> artifacts) throws MojoFailureException, MojoExecutionException {
        try {
            List<ArtifactRequest> requests = Utils.artifactRequests(artifacts, getSourceRepositories());
            List<ArtifactResult> downloaded = artifactResolver.resolveArtifacts(session.getRepositorySession(), requests);
            for (ArtifactResult result : downloaded) {
                Utils.checkResult(result, e -> false);
            }
            return downloaded;
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve artifacts", ex);
        }
    }

    protected List<Artifact> getExistingArtifacts(List<Artifact> artifacts) throws MojoFailureException, MojoExecutionException {
        Map<Boolean, List<Artifact>> partitioned = artifacts.stream().collect(Collectors.partitioningBy(Utils::isPom));
        List<Artifact> discovered = new ArrayList<>();
        discovered.addAll(checkNonPomArtifacts(partitioned.getOrDefault(false, Collections.emptyList())));
        discovered.addAll(loadPomsRecursively(partitioned.getOrDefault(true, Collections.emptyList())));
        discovered.sort(Comparator.comparing(Artifact::toString));
        return discovered;
    }

    protected List<Artifact> checkNonPomArtifacts(List<Artifact> nonpoms) throws MojoFailureException, MojoExecutionException {
        try {
            List<Artifact> discovered = new ArrayList<>();
            RepositorySystemSession repositorySession = session.getRepositorySession();
            List<ArtifactRequest> requests = Utils.artifactRequests(nonpoms, getSourceRepositories());
            List<ArtifactResult> sourceArtifacts = repositoryArtifactChecker.checkArtifacts(repositorySession, false, requests);
            for (ArtifactResult result : sourceArtifacts) {
                Utils.checkResult(result, ArtifactNotFoundException.class::isInstance);
                if (result.getArtifact() != null) {
                    discovered.add(result.getArtifact());
                }
            }
            return discovered;
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve source artifacts", ex);
        }
    }

    protected List<Artifact> loadPomsRecursively(List<Artifact> poms) throws MojoFailureException, MojoExecutionException {
        try {
            List<Artifact> discovered = new ArrayList<>();
            DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession(session.getRepositorySession());
            ModelAwareArtifactDescriptorReader descriptorReader = new ModelAwareArtifactDescriptorReader();
            repositorySession.setConfigProperty(ArtifactDescriptorReaderDelegate.class.getName(), descriptorReader);
            repositorySession.setArtifactDescriptorPolicy((s, r) -> ArtifactDescriptorPolicy.IGNORE_ERRORS);
            Set<String> seen = new HashSet<>();
            while (!poms.isEmpty()) {
                List<Artifact> next = new ArrayList<>();
                for (Artifact artifact : poms) {
                    if (!seen.add(Utils.getId(artifact))) {
                        continue;
                    }
                    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(artifact, getSourceRepositories(), null);
                    ArtifactDescriptorResult descriptor = artifactDescriptorReader.readArtifactDescriptor(repositorySession, request);
                    if (descriptor == null) {
                        continue;
                    }
                    Model model = descriptorReader.getModel(artifact);
                    if (model == null) {
                        continue;
                    }
                    discovered.add(descriptor.getArtifact());
                    Parent parent = model.getParent();
                    if (parent != null) {
                        next.add(Utils.getPomArtifact(parent));
                    }
                    DependencyManagement dependencyManagement = model.getDependencyManagement();
                    if (dependencyManagement == null) {
                        continue;
                    }
                    for (org.apache.maven.model.Dependency dependency : dependencyManagement.getDependencies()) {
                        if (!"import".equals(dependency.getScope())) {
                            continue;
                        }
                        next.add(Utils.getPomArtifact(dependency));
                    }
                }
                poms = next;
            }
            return discovered;
        } catch (ArtifactDescriptorException ex) {
            throw new MojoExecutionException("Failed to load poms", ex);
        }
    }

    protected DeployRequest createDeployRequest(List<ArtifactResult> artifacts) throws MojoFailureException {
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
            ArtifactRepositoryPolicy updateAlways = Utils.getUpdateAlwaysPolicy();
            ArtifactRepository repository = parseRepository(targetRepository, updateAlways);
            target = getRemoteRepositories(Collections.singletonList(repository), false).get(0);
        }
        return target;
    }

    protected List<RemoteRepository> getSourceRepositories() throws MojoFailureException {
        if (source == null) {
            ArtifactRepositoryPolicy updateAlways = Utils.getUpdateAlwaysPolicy();
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

    protected Artifact getArtifact(org.apache.maven.model.Dependency coordinate) {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(coordinate.getType());
        return new DefaultArtifact(
                coordinate.getGroupId(),
                coordinate.getArtifactId(),
                coordinate.getClassifier(),
                artifactHandler.getExtension(),
                coordinate.getVersion()
        );
    }

}
