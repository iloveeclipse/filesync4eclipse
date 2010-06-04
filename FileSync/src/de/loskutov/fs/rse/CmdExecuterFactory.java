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
package de.loskutov.fs.rse;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

import de.loskutov.fs.FileSyncException;
import de.loskutov.fs.builder.SyncWizard;
import de.loskutov.fs.rse.utils.RemoteFileImpl;
import de.loskutov.fs.rse.utils.RseSimpleUtils;
import de.loskutov.fs.rse.utils.RseUtils;

public class CmdExecuterFactory {

    private static CmdExecuterFactory instance;

    private final Map<IPath, CmdExecuter> cmdExecuterMap = new HashMap<IPath, CmdExecuter>();

    private CmdExecuterFactory() {
        /* Singleton */
    }

    public static CmdExecuterFactory getInstance() {
        if (instance == null) {
            instance = new CmdExecuterFactory();
        }
        return instance;
    }

    public CmdExecuter getCmdExecuter(IPath destinationRoot) {
        CmdExecuter ret = cmdExecuterMap.get(destinationRoot);
        if (ret == null) {
            File destinationFile = SyncWizard.getDestinationFile(destinationRoot);
            IRemoteFile remoteFile = null;
            if (RseSimpleUtils.isRseFile(destinationFile)) {
                remoteFile = ((RemoteFileImpl) destinationFile).getRemoteFile();
            } else {
                try {
                    remoteFile = RseUtils.getLocalFileSubSystem().getRemoteFileObject(
                            destinationFile.getAbsolutePath(), null);
                } catch (SystemMessageException e) {
                    throw new FileSyncException(e);
                }
            }

            if (RseUtils.isUnixStyle(remoteFile)) {
                ret = new RseUnixCmdExecuter(remoteFile);
            } else if (RseUtils.isWindows(remoteFile)) {
                if (RseSimpleUtils.isRseFile(destinationFile)) {
                    ret = new RseWindowsCmdExecuter(remoteFile);
                } else {
                    ret = new WindowsLocalCmdExecuter(remoteFile);
                }
            } else {
                throw new FileSyncException("UnsupportedOperationSystem (RSE Host '"
                        + remoteFile.getHost() + "'");
            }
            cmdExecuterMap.put(destinationRoot, ret);

        }
        return ret;
    }

}
