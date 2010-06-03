/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.command;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.core.runtime.IPath;

public class FsPathUtilImpl implements FsPathUtil {

    public boolean isUriIncluded(IPath path) {
        return FsUriPath.isUriIncluded(path);
    }

    public IPath create(String path) {
        return FsUriPath.create(path);
    }

    public IPath create(IPath path, String newPath) {
        return FsUriPath.create(path, newPath);
    }

    public boolean isUri(String pathStr) {
        return isUriLike(pathStr);
    }

    // public URI getUri(IPath path) {
    // throw new UnsupportedOperationException();
    // }
    public URI getUri(IPath path) {
        if (!(path instanceof FsUriPath)) {
            throw new IllegalArgumentException();
        }
        FsUriPath fsUriPath = (FsUriPath) path;
        return fsUriPath.getUri();
    }

    public OutputStream getOutputStream(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    /*
     * return null if((path instanceof FsUriPath) and uri.scheme != "file". A file otherwise.
     */
    public File toFile(IPath path) {
        if (path instanceof FsUriPath) {
            FsUriPath fsUriPath = (FsUriPath) path;
            if (FsUriPath.FILE_SCHEME_TOKEN.equals(fsUriPath.getUri().getScheme())) {
                return new File(fsUriPath.getUri());
            }
        } else {
            return path.toFile();
        }
        return null;
    }

    public static boolean isUriLike(String pathStr) {
        return pathStr.matches("^(file|rse):.*");
    }

    public String toFqString(IPath path) {
        if (path instanceof FsUriPath) {
            FsUriPath fsUriPath = (FsUriPath) path;
            return fsUriPath.getUri().toString();
        }
        return path.toString();
    }

    public boolean isRseUnc(IPath path) {
        if (path instanceof FsUriPath) {
            FsUriPath fsUriPath = (FsUriPath) path;
            return fsUriPath.getUri().getScheme().equalsIgnoreCase(FsUriPath.RSE_SCHEME_TOKEN)
            && path.isUNC();
        }
        return false;
    }

}
