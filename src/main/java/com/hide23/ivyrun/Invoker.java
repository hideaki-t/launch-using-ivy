package com.hide23.ivyrun;

import java.lang.reflect.Method;

public class Invoker {
    public static void main(String[] args) throws Exception {
        // using the classloader set by Ivy instead of just calling Class.forName
        Class c = Thread.currentThread().getContextClassLoader().loadClass("clojure.main");
        Method m = c.getMethod("main", new Class[]{String[].class});
        m.invoke(null, new Object[]{new String[]{"-e", "(+ 1 2 3)"}});
    }
}
