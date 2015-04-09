package com.hide23.ivyrun;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Execute an executable jar from artifactory
 */
public class IvyRun {
    Path setting;

    /**
     * @param repos repositories. will be used as resolvers in a chain resolver.
     *              if it is empty, default BintrayResolver will be used
     */
    public IvyRun(List<URI> repos) {
        try {
            setting = Files.createTempFile("", "");
            try (Writer w = Files.newBufferedWriter(setting, StandardCharsets.UTF_8)) {
                XMLStreamWriter x = XMLOutputFactory.newInstance().createXMLStreamWriter(w);
                x.writeStartDocument();
                x.writeStartElement("ivysettings");
                x.writeStartElement("settings");
                x.writeAttribute("defaultResolver", "chain");
                x.writeEndElement();
                x.writeStartElement("caches");
                x.writeEndElement();
                x.writeStartElement("resolvers");
                x.writeStartElement("chain");
                x.writeAttribute("name", "chain");
                if (repos.isEmpty()) {
                    x.writeStartElement("bintray");
                    x.writeEndElement();
                } else {
                    for (URI u : repos) {
                        x.writeStartElement("ibiblio");
                        x.writeAttribute("m2compatible", "true");
                        x.writeAttribute("root", u.toString());
                        x.writeEndElement();
                    }
                }
                x.writeEndElement();
                x.writeEndElement();
                x.writeEndDocument();
            }
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
        }
    }

    public IvyRun(URI... repos) {
        this(Arrays.asList(repos));
    }

    public void run(String organization, String name, String rev, String mainClass, String... args) throws Exception {
        List<String> a = new ArrayList<>(8 + args.length);
        a.add("-settings");
        a.add(setting.toFile().getPath());
        a.add("-dependency");
        a.add(organization);
        a.add(name);
        a.add(rev);
        a.add("-main");
        a.add(mainClass);
        a.addAll(Arrays.asList(args));
        org.apache.ivy.Main.main(a.toArray(new String[0]));
    }
}
