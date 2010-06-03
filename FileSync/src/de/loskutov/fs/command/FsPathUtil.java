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
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.core.runtime.IPath;

public interface FsPathUtil {

    public boolean isUriIncluded(IPath path);

    public boolean isUri(String pathStr);

    public IPath create(String path);

    public IPath create(IPath path, String newPath);

    public URI getUri(IPath path);

    public OutputStream getOutputStream(File file) throws FileNotFoundException;

    public File toFile(IPath path);

    /**
     * rse://local//UncServer//UncName//path did copy it to c:\UncServer\UncName\path under windows
     *
     * @param path
     * @return true if rse-uri and isUnc(). false otherwiser
     */
    public boolean isRseUnc(IPath path);

    /**
     * to fully qualified String
     *
     * @param path
     * @return the uri if path instanceof FSUriPath. path.toString() otherwise.
     */
    public String toFqString(IPath path);
}
