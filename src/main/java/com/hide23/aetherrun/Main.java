package com.hide23.aetherrun;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import javax.script.ScriptException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

/**
 * run a main class with dependencies
 */
public class Main {
    public static void main(String[] args) throws MalformedURLException, DependencyCollectionException, DependencyResolutionException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, ScriptException {
        AetherRun runner = new AetherRun(Paths.get("target/local-repo"), new URL("http://repo1.maven.org/maven2/"));
        // invoke a main class in the caller which uses the dependencies
        runner.run("com.hide23.ivyrun.Invoker", new String[]{}, "org.clojure:clojure:1.7.0");
        // invoke a main class in the dependencies
        runner.run("clojure.main", new String[]{"-e", "(+ 1 2 3)"}, "org.clojure:clojure:1.7.0");
        // run javascript code
        runner.runScript("Packages.clojure.main.main(['-e', '(+ 1 2 3)']);", "js", "org.clojure:clojure:1.7.0");
    }
}
