/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.builder.cmdexecuter;

import java.io.File;

import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

import de.loskutov.fs.builder.DelayedSyncWizard.DeleteFileRecord;

public class RseWindowsCmdExecuter extends RseUnixCmdExecuter {

    public RseWindowsCmdExecuter(IRemoteFile workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public String[] getDeleteCommands(File contentFile) {
        String[] ret = new String[] { quote(contentFile.getAbsolutePath()) };
        return ret;
    }

    @Override
    public String[] getUnzipCommands(File zipFile) {
        String[] ret = new String[] { getUnzipCmd() + " " + quote(zipFile.getAbsolutePath()) };
        return ret;
    }

    @Override
    public String toStringForDelete(DeleteFileRecord fileRecord) {
        return (fileRecord.isDirectory()? "rmdir ":"del ") + quote(fileRecord.getTargetName());
    }

    @Override
    public String getFilesToDeleteSuffix(){
        return ".bat";
    }
    public String getFileQuote(){
        return RseUnixCmdExecuter.DOUBLE_QUOTE;
    }


}
