package com.google.cloud.tools.jib.tar;

import co.elastic.gradle.utils.ExtractCompressedTar;

import java.io.IOException;
import java.nio.file.Path;

public class TarExtractor {

    // HACK: Jib doesn't support compressed tar archives, see https://github.com/GoogleContainerTools/jib/issues/2895
    //       Thus we replace the class that deals with extracting tar archives to add support for it.
    //       Ideally we'll contribute it upstream and will be able to remove the hack.
    public static void extract(Path source, Path destination) throws IOException {
        ExtractCompressedTar.extract(source, destination);
    }

}
