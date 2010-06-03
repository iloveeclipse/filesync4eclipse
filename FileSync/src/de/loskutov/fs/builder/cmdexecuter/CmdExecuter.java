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
import java.util.Collection;
import java.util.List;

import de.loskutov.fs.builder.DelayedSyncWizard;
import de.loskutov.fs.builder.DelayedSyncWizard.DeleteFileRecord;

public interface CmdExecuter {

    /**
     * when using rse-shell to a linux-remote-system, there stderr-stream is part of stdout and
     * can't be distinguished easily So this String should be added to a cmd in a way like
     * "cmd || echo " + ERROR_SIGNAL //TODO: I didn't tested ssh to a windows-remote-system
     */
    String ERROR_SIGNAL = "errorOfRseCommandWhichShouldBeAFairlyUniqueString";

    boolean execute(String[] commands);

    String[] getDeleteCommands(File contentFile);

    String[] getUnzipCommands(File zipFile);

    String getLineSeparator();

    /**
     * this is used in {@link DelayedSyncWizard#commit()}.
     *
     * @param fileRecord
     * @return a String-representation of the file. On Default it's the
     *         {@link File#getAbsolutePath()}.
     */
    String toStringForDelete(DeleteFileRecord fileRecord);
    List<String> toStringsForDelete(Collection<DeleteFileRecord> fileNames);
    /**
     * @return ".txt" if it is just a list of files. ".bat" on Windows as it is a list of commands
     */
    String getFilesToDeleteSuffix();
    String getFileQuote();
    String quote(String stringToQuote);
}
