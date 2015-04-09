package com.hide23.ivyrun;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.retrieve.RetrieveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.BintrayResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Execute an executable jar from artifactory
 */
public class IvyRun {
    private final Ivy ivy;

    /**
     * @param repos repositories. will be used as resolvers in a chain resolver.
     *              if it is empty, default BintrayResolver will be used
     */
    public IvyRun(List<URI> repos) {
        IvySettings setting = new IvySettings();
        ivy = Ivy.newInstance(setting);
        ChainResolver resolver = new ChainResolver();
        resolver.setReturnFirst(true);
        resolver.setName("chained");
        convert(repos).forEach(x -> resolver.add(x));
        setting.addResolver(resolver);
        setting.setDefaultResolver(resolver.getName());
    }

    public IvyRun(URI... repos) {
        this(Arrays.asList(repos));
    }

    private static BintrayResolver c() {
        BintrayResolver resolver = new BintrayResolver();
        resolver.setM2compatible(true);
        return resolver;
    }

    private static DependencyResolver c(URI u) {
        BintrayResolver resolver = c();
        resolver.setName(u.toString());
        resolver.setUsepoms(true);
        resolver.setRoot(u.toString());
        return resolver;
    }

    private Stream<DependencyResolver> convert(List<URI> repos) {
        if (repos.isEmpty()) {
            return Stream.of(c());
        }
        return repos.stream().map(IvyRun::c);
    }

    private ModuleDescriptor makeDescriptor(String org, String name, String rev) {
        DefaultModuleDescriptor desc = DefaultModuleDescriptor.newDefaultInstance(
                ModuleRevisionId.newInstance("fake-organization", name, rev)
        );

        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(desc,
                ModuleRevisionId.newInstance(org, name, rev), false, false, true);
        dd.addDependencyConfiguration("default", "*");
        desc.addDependency(dd);
        return desc;
    }

    // add run on another JVM?
    private void execute(Path jarName, String mainClass, String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, MalformedURLException {
        ClassLoader cl = new URLClassLoader(new URL[]{jarName.toUri().toURL()});
        Class c = cl.loadClass(mainClass);
        Thread.currentThread().setContextClassLoader(cl);
        String[] ag = new String[]{};
        Method m = c.getMethod("main", new Class[]{ag.getClass()});
        m.invoke(null, new Object[]{ag});
    }

    public void run(String org, String name, String rev, String mainClass, String... args) throws IOException, ParseException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        ModuleDescriptor desc = makeDescriptor(org, name, rev);
        ResolveReport result = ivy.resolve(desc, new ResolveOptions());
        ModuleDescriptor md = result.getModuleDescriptor();
        ivy.retrieve(md.getModuleRevisionId(),
                "out/[artifact](-[classifier]).[ext]",
                new RetrieveOptions().setConfs(new String[]{"default"}));
        execute(Paths.get("out", name + ".jar"), mainClass, args);
    }
}
