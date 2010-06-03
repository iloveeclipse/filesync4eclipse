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
    public static final String ERROR_SIGNAL = "errorOfRseCommandWhichShouldBeAFairlyUniqueString";

    public boolean execute(String[] commands);

    public String[] getDeleteCommands(File contentFile);

    public String[] getUnzipCommands(File zipFile);

    public String getLineSeparator();

    /**
     * this is used in {@link DelayedSyncWizard#commitDeletes()}.
     * 
     * @param file
     * @return a String-representation of the file. On Default it's the
     *         {@link File#getAbsolutePath()}.
     */
    public String toStringForDelete(DeleteFileRecord fileRecord);
    public List<String> toStringsForDelete(Collection<DeleteFileRecord> fileNames);
    /**
     * @return ".txt" if it is just a list of files. ".bat" on Windows as it is a list of commands
     */
    public String getFilesToDeleteSuffix();
    public String getFileQuote();
    public String quote(String stringToQuote);
}
