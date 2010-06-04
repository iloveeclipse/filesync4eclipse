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
package de.loskutov.fs.rse.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.ISystemProfile;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.services.files.IFileService;
import org.eclipse.rse.subsystems.files.core.model.RemoteFileUtility;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFileSubSystem;

import de.loskutov.fs.FileSyncException;
import de.loskutov.fs.utils.NamedOutputStream;

public class RseUtils {

    private RseUtils() {/* just static methods */
    }

    public static OutputStream getOutputStream(File file) throws FileNotFoundException {
        OutputStream out = null;
        if (file instanceof RemoteFileImpl) {
            RemoteFileImpl uFile = (RemoteFileImpl) file;
            try {
                out = uFile.getRemoteFile().getParentRemoteFileSubSystem().getOutputStream(
                        uFile.getRemoteFile().getParentPath(),
                        uFile.getRemoteFile().getName(),
                        uFile.getRemoteFile().isBinary() ? IFileService.NONE
                                : IFileService.TEXT_MODE, null);
                out = new NamedOutputStream(out, file.toString());
            } catch (SystemMessageException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            out = new FileOutputStream(file);
        }
        return out;
    }

    public static IHost getHost(String hostName) throws Exception {
        if (hostName == null) {
            return null;
        }
        ISystemRegistry registry = RSECorePlugin.getDefault().getSystemRegistry();
        ISystemProfile profile = registry.getSystemProfileManager()
        .getDefaultPrivateSystemProfile();
        IHost host = registry.getHost(profile, hostName);
        if (host.isOffline()) {
            throw new FileSyncException("Connect to Host first");
        }
        return host;
    }

    public static File getRseFile(IHost host, String fileName) throws Exception {
        IRemoteFileSubSystem ffs = RemoteFileUtility.getFileSubSystem(host);
        IRemoteFile remoteFileObject = ffs.getRemoteFileObject(fileName, null);

        return new RemoteFileImpl(remoteFileObject);
    }

    /**
     * Copied and modified from @link
     * org.eclipse.rse.files.ui.resources.UniversalFileTransferUtility#getLocalFileSubSystem() as
     * it's private there. Helper method to get the local host.
     *
     * @return the local file subsystem
     */
    public static IHost getLocalHost() {
        ISystemRegistry registry = RSECorePlugin.getTheSystemRegistry();
        IHost[] connections = registry.getHosts();
        for (int i = 0; i < connections.length; i++) {
            IHost connection = connections[i];
            IRemoteFileSubSystem anFS = RemoteFileUtility.getFileSubSystem(connection);
            if (anFS.getHost().getSystemType().isLocal()) {
                return connection;
            }
        }
        return null;
    }

    /**
     * Copied and modified from @link
     * org.eclipse.rse.files.ui.resources.UniversalFileTransferUtility as it's private there. Helper
     * method to get the local file subsystem.
     *
     * @return the local file subsystem
     */
    public static IRemoteFileSubSystem getLocalFileSubSystem() {
        IHost localHost = getLocalHost();
        if (localHost == null) {
            return null;
        }

        return RemoteFileUtility.getFileSubSystem(localHost);
    }

    public static boolean isUnixStyle(IRemoteFile remoteFile) {
        return remoteFile.getParentRemoteFileSubSystem()
        .getParentRemoteFileSubSystemConfiguration().isUnixStyle();
    }

    public static boolean isWindows(IRemoteFile remoteFile) {
        return remoteFile.getHost().getSystemType().isWindows();
    }

}
