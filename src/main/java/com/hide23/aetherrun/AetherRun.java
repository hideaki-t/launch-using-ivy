package com.hide23.aetherrun;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * a main class runner with resolving dependency
 */
public class AetherRun {
    private static final Logger logger = LoggerFactory.getLogger(AetherRun.class);

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepos;

    public AetherRun(Path localRepo, List<RemoteRepository> remoteRepos) {
        repoSystem = newRepositorySystem();
        session = newSession(repoSystem, localRepo);
        this.remoteRepos = remoteRepos;
    }

    public AetherRun(Path localRepo, URL remoteRepo) {
        this(localRepo, Collections.singletonList(
                new RemoteRepository.Builder("default", "default", remoteRepo.toString()).build()));
    }

    public AetherRun(Path localRepo) {
        this(localRepo, Collections.singletonList(
                new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build()));
    }

    private static List<URL> dependenciesToUrls(List<DependencyNode> dependencies) {
        return dependencies.stream()
                .filter(x -> x.getDependency() != null)
                .map(x -> x.getDependency().getArtifact())
                .filter(x -> x.getFile() != null)
                .map(wrap(x -> x.getFile().toURI().toURL()))
                .collect(Collectors.toList());
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(RepositorySystem system, Path localRepoPath) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    public Object run(String mainClass, String[] args, String rootArtifact) throws DependencyCollectionException, DependencyResolutionException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return run(mainClass, args, resolve(rootArtifact));
    }

    public Object run(String mainClass, String[] args, List<DependencyNode> dependencies) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        List<URL> jarPaths = dependenciesToUrls(dependencies);
        URLClassLoader cl = new URLClassLoader(jarPaths.toArray(new URL[jarPaths.size()]));
        Class<?> clz = cl.loadClass(mainClass);
        Thread.currentThread().setContextClassLoader(cl);
        Method m = clz.getMethod("main", args.getClass());
        return m.invoke(null, new Object[]{args});
    }

    public Object runScript(String script, String engineName, String rootArtifact) throws ScriptException, DependencyCollectionException, DependencyResolutionException {
        return runScript(script, engineName, resolve(rootArtifact));
    }

    public Object runScript(String script, String engineName, List<DependencyNode> dependencyNodes) throws ScriptException {
        List<URL> jarPaths = dependenciesToUrls(dependencyNodes);
        URLClassLoader cl = new URLClassLoader(jarPaths.toArray(new URL[jarPaths.size()]));
        ScriptEngineManager manager = new ScriptEngineManager(cl);
        ScriptEngine engine = manager.getEngineByName(engineName);
        if (engine == null) {
            throw new ScriptException("ScriptEngine " + engineName + " not found");
        }
        logger.debug("ScripeEngineName: {},  Engine: {}", engineName, engine.getFactory().getEngineName());
        return engine.eval(script);
    }

    public List<DependencyNode> resolve(String rootArtifact) throws DependencyCollectionException, DependencyResolutionException {
        return resolve(rootArtifact, Collections.emptyList());
    }

    public List<DependencyNode> resolve(String rootArtifact, List<String> extraArtifacts) throws DependencyCollectionException, DependencyResolutionException {
        CollectRequest collectRequest = new CollectRequest();
        if (rootArtifact != null) {
            logger.debug("setting root: {}", rootArtifact);
            collectRequest.setRoot(new Dependency(new DefaultArtifact(rootArtifact), "compile"));
        }

        List<Dependency> dependencies = extraArtifacts.stream()
                .map(n -> new Dependency(new DefaultArtifact(n), "compile"))
                .collect(Collectors.toList());
        collectRequest.setDependencies(dependencies);
        collectRequest.setRepositories(remoteRepos);
        DependencyNode node = repoSystem.collectDependencies(session, collectRequest).getRoot();
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setRoot(node);
        logger.debug("collect: {}", collectRequest);
        logger.debug("root: {}", node);

        logger.info("start resolving dependency: {}", dependencyRequest);
        DependencyResult result = repoSystem.resolveDependencies(session, dependencyRequest);
        logger.info("result: {}", result);
        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);
        logger.debug("classpath: {}", nlg.getClassPath());
        return nlg.getNodes();
    }

    @FunctionalInterface
    private interface ThrowableFunction<T, R> {
        R apply(T t) throws Exception;
    }

    private static <T, R> Function<T, R> wrap(ThrowableFunction<T, R> f) {
        return o -> {
            try {
                return f.apply(o);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
