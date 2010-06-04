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

import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

import de.loskutov.fs.rse.BulkSyncWizard.DeleteFileRecord;

public class RemoteWindowsCommandExecutor extends RemoteUnixCommandExecutor {

    public RemoteWindowsCommandExecutor(IRemoteFile workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public String getDeleteCommand(File contentFile) {
        return quote(contentFile.getAbsolutePath());
    }

    @Override
    public String getUnzipCommand(File zipFile) {
        return getUnzipCmd() + " " + quote(zipFile.getAbsolutePath());
    }

    @Override
    public String toStringForDelete(DeleteFileRecord fileRecord) {
        // XXX should go to the props page
        return (fileRecord.isDirectory() ? "rmdir " : "del ") + quote(fileRecord.getTargetName());
    }

    @Override
    public String getFilesToDeleteSuffix() {
        return ".bat";
    }

    @Override
    public String getFileQuote() {
        return RemoteUnixCommandExecutor.DOUBLE_QUOTE;
    }

}
