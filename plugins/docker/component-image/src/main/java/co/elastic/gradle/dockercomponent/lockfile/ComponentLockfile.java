package co.elastic.gradle.dockercomponent.lockfile;


import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.UnchangingContainerReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.gradle.api.tasks.Input;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

public record ComponentLockfile(Map<Architecture, UnchangingContainerReference> images) {

    @Input
    public Map<Architecture, UnchangingContainerReference> getImages() {
        return images();
    }

    public static ComponentLockfile parse(Reader reader) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(reader, ComponentLockfile.class);
    }

    public static void write(ComponentLockfile lockfile, Writer writer) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        writer.write("# THIS IS AN AUTOGENERATED FILE. DO NOT EDIT THIS FILE DIRECTLY.\n");
        mapper.writeValue(writer, lockfile);
    }



}