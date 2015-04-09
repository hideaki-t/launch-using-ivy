package com.hide23.ivyrun;

import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        // Ivy's cli main calls System.exit, so calls below will never return.
        // with ivy.xml and call an internal class
        new IvyRun().run(Paths.get("ivy.xml"), "com.hide23.ivyrun.Invoker");
        // without ivy.xml and call an internal class
        new IvyRun().run("org.clojure", "clojure", "1.6.0", "com.hide23.ivyrun.Invoker");
        // without ivy.xml, run main method in the given dependency
        new IvyRun().run("org.clojure", "clojure", "1.6.0", "clojure.main", "--", "-e", "(+ 1 2 3)");
        // with ivy.xml, run main method in the given dependency
        new IvyRun().run(Paths.get("ivy.xml"), "clojure.main", "--", "-e", "(+ 1 2 3)");
    }
}
