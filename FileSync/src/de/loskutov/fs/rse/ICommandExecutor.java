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
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;

import de.loskutov.fs.rse.BulkSyncWizard.DeleteFileRecord;

public interface ICommandExecutor {

    boolean execute(String command, IProgressMonitor monitor);

    String getDeleteCommand(File contentFile);

    String getUnzipCommand(File zipFile);

    String getLineSeparator();

    List<String> toStringsForDelete(Collection<DeleteFileRecord> fileNames);

    /**
     * @return ".txt" if it is just a list of files. ".bat" on Windows as it is a list of commands
     */
    String getFilesToDeleteSuffix();

}
