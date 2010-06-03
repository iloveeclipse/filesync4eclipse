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

import org.eclipse.core.runtime.IPath;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

import de.loskutov.fs.utils.RseUtils;

public class FsUriPathUtil extends FsPathUtilImpl {

    @Override
    public OutputStream getOutputStream(File file) throws FileNotFoundException {
        return RseUtils.getOutputStream(file);
    }

    @Override
    public File toFile(IPath path) {
        if (path == null) {
            return null;
        }
        if (!(path instanceof FsUriPath)) {
            return path.toFile();
        }
        FsUriPath fsUriPath = (FsUriPath) path;

        if (fsUriPath.getUri().getScheme().equals(FsUriPath.FILE_SCHEME_TOKEN)) {
            return new File(fsUriPath.getUri());
        }
        if (isRseUnc(fsUriPath)) {
            throw new IllegalArgumentException("UNC via RSE is not supported yet (Path '"
                    + toFqString(fsUriPath) + "').");
        }

        IHost host = null;
        boolean error = false;
        try {
            host = RseUtils.getHost(fsUriPath.getUri().getHost());
            //            if(host.getSystemType()){}
        } catch (Exception e) {
            error = true;
        }

        if (error || host == null) {
            throw new FileSyncException("Failed to get a host with name '"
                    + fsUriPath.getUri().getHost()
                    + "'. Add a connection to the View 'Remote Systems' or change the hostName");
        }
        try {

            File destFile = RseUtils.getRseFile(host, fsUriPath.toPortableString());
            return destFile;
        } catch (Exception e) {
            throw new FileSyncException(e);
        }
    }

    public static FsUriPath create(IRemoteFile remoteFile) {
        if (remoteFile == null) {
            return null;
        }

        return FsUriPath
        .create("rse", remoteFile.getHost().getName(), remoteFile.getAbsolutePath());
    }

}
