package com.metalake.worker.api.v1;

import java.net.URI;
import java.net.URISyntaxException;

import static com.metalake.worker.api.v1.ApiModels.FilesetRef;

/**
 * Builds an original-source Fileset root URI, or the currently published Git
 * mirror Fileset root URI, using the public GVFS URI convention.
 *
 * Metalake is selected through the GVFS client's configuration and therefore
 * is not part of the gvfs://fileset/<catalog>/<schema>/<fileset>/ path.
 */
public final class GvfsSourceUri {
    private GvfsSourceUri() {}

    public static String from(FilesetRef fileset) {
        if (fileset == null || !valid(fileset.catalog()) || !valid(fileset.schema())
                || !valid(fileset.name())) {
            return null;
        }
        String path = "/" + fileset.catalog() + "/" + fileset.schema()
                + "/" + fileset.name() + "/";
        try {
            // URI encodes reserved characters, e.g. spaces, in path segments.
            return new URI("gvfs", "fileset", path, null, null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid Gravitino Fileset identifier", ex);
        }
    }

    private static boolean valid(String part) {
        return part != null && !part.isBlank() && !part.equals(".") && !part.equals("..")
                && part.indexOf('/') < 0 && part.indexOf('\\') < 0 && part.indexOf('\0') < 0;
    }
}
