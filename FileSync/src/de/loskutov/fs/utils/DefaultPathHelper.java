/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *  Andrei Loskutov - refactoring
 *******************************************************************************/
package de.loskutov.fs.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.core.runtime.IPath;

import de.loskutov.fs.IPathHelper;

public class DefaultPathHelper implements IPathHelper {
    private static final String FS_PATH_UTIL_CLASS_NAME = "de.loskutov.fs.command.FsUriPathUtil";
    private static boolean errorOnLoadOfFsPathUtil;
    private static IPathHelper iPathHelper;

    public static IPathHelper getPathHelper() {
        if (iPathHelper == null) {
            iPathHelper = createIPathHelper();
        }
        return iPathHelper;
    }

    @SuppressWarnings("unchecked")
    private static IPathHelper createIPathHelper() {
        if (!errorOnLoadOfFsPathUtil) {
            try {
                Class<IPathHelper> wizardClass = (Class<IPathHelper>) Class.forName(
                        FS_PATH_UTIL_CLASS_NAME, true, IPathHelper.class.getClassLoader());
                return wizardClass.newInstance();
            } catch (Throwable e) {
                errorOnLoadOfFsPathUtil = true;
            }
        }
        return new DefaultPathHelper();
    }

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

    /**
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

    public boolean isRseFile(File path) {
        return false;
    }

}
