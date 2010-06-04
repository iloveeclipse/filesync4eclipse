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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.subsystems.IRemoteSystemEnvVar;
import org.eclipse.rse.shells.ui.RemoteCommandHelpers;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCmdSubSystem;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCommandShell;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteError;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteOutput;

import de.loskutov.fs.FileSyncException;
import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.command.FS;
import de.loskutov.fs.rse.BulkSyncWizard.DeleteFileRecord;
import de.loskutov.fs.rse.utils.RseSimpleUtils;
import de.loskutov.fs.rse.utils.RseUtils;

public class RemoteUnixCommandExecutor implements ICommandExecutor {

    /**
     * when using rse-shell to a linux-remote-system, there stderr-stream is part of stdout and
     * can't be distinguished easily So this String should be added to a cmd in a way like
     * "cmd || echo " + ERROR_SIGNAL //TODO: I didn't tested ssh to a windows-remote-system
     */
    private static final String ERROR_SIGNAL = "errorOfRseCommandWhichShouldBeAFairlyUniqueString";
    private static final String SINGLE_QUOTE = "'";
    protected static final String DOUBLE_QUOTE = "\"";

    private static final long NO_TIME_OUT_IN_MILLIS = -1;
    private static final String KEY_FILESYNC_UNZIP_CMD = "FILESYNC_UNZIP_CMD";
    private static final String END_OF_RSE_COMMAND = "endOfRseCommandWhichShouldBeAFairlyUniqueString";

    private final IRemoteCmdSubSystem cmdSS;
    protected final IRemoteFile workingDirectory;

    public RemoteUnixCommandExecutor(IRemoteFile workingDirectory) {
        this.workingDirectory = workingDirectory;
        cmdSS = RemoteCommandHelpers.getCmdSubSystem(getHost());
    }

    public IHost getHost() {
        return workingDirectory.getHost();
    }

    public String getLineSeparator() {
        return workingDirectory.getLineSeparator();
    }

    public boolean execute(String command, IProgressMonitor monitor) {
        boolean ok = true;
        IRemoteCommandShell cmdShell = getCmdShell(command);
        try {
            if (monitor.isCanceled()) {
                return false;
            }
            cmdSS.sendCommandToShell(command, cmdShell, null);
            String eoCmd = "echo " + END_OF_RSE_COMMAND;
            cmdSS.sendCommandToShell(eoCmd, cmdShell, null);
            // XXX should go to the props page
            getOutputUntil(cmdShell, END_OF_RSE_COMMAND, 5000, 2000, monitor);
        } catch (Exception e) {
            FileSyncPlugin.log("not executed.", e, IStatus.WARNING);
            ok = false;
        } finally {
            cmdShell.removeOutput();
            try {
                cmdSS.cancelShell(cmdShell, monitor);
                cmdSS.removeShell(cmdShell);
            } catch (Exception e) {
                FileSyncPlugin.log("CmdShell not closed: ", e, IStatus.WARNING);
                ok = false;
            }
        }
        return ok;

    }

    public String getFileQuote() {
        return SINGLE_QUOTE;
    }

    public String getDeleteCommand(File contentFile) {
        // XXX should go to the props page
        StringBuilder sb1 = new StringBuilder(" cat ");
        sb1.append(quote(contentFile.getName()));
        sb1.append(" | xargs rm -f -r -v ");
        sb1.append(" || echo " + ERROR_SIGNAL).append(";");
        return sb1.toString();
    }

    public String getUnzipCommand(File zipFile) {
        // XXX should go to the props page
        StringBuilder sb = new StringBuilder("cd ");
        sb.append(quote(zipFile.getParent()));
        sb.append(" && ").append(getUnzipCmd()).append(" ").append(quote(zipFile.getName()));
        sb.append(" || echo " + ERROR_SIGNAL);
        return sb.toString();
    }

    /**
     * this is used in {@link BulkSyncWizard#commit(IProgressMonitor)}.
     *
     * @param fileRecord
     * @return a String-representation of the file. On Default it's the
     *         {@link File#getAbsolutePath()}.
     */
    public String toStringForDelete(DeleteFileRecord fileRecord) {
        return quote(fileRecord.getTargetName());
    }

    public List<String> toStringsForDelete(Collection<DeleteFileRecord> fileRecords) {
        if (fileRecords == null) {
            return null;
        }

        List<String> ret = new ArrayList<String>(fileRecords.size());
        for (DeleteFileRecord fileRecord : fileRecords) {
            ret.add(toStringForDelete(fileRecord));
        }
        return ret;

    }

    public String getFilesToDeleteSuffix() {
        return ".txt";
    }

    public String quote(String stringToQuote) {
        return new StringBuilder(getFileQuote()).append(stringToQuote).append(getFileQuote())
                .toString();
    }

    protected String getUnzipCmd() {
        String ret = null;
        if (cmdSS.getHost().getSystemType().isLocal()) {
            ret = System.getenv(KEY_FILESYNC_UNZIP_CMD);
        }
        IRemoteSystemEnvVar envVar = cmdSS.getEnvironmentVariable(KEY_FILESYNC_UNZIP_CMD);

        if (envVar != null) {
            ret = envVar.getValue();
        } else {
            // XXX should go to the props page
            if (RseUtils.isWindows(workingDirectory)) {
                ret = "jar -xfv ";
            } else {
                ret = "unzip -o";
            }
        }
        return ret;
    }

    private IRemoteCommandShell getCmdShell(String commands) {
        try {
            return cmdSS.runShell(workingDirectory, null);
        } catch (Exception e) {
            throw new FileSyncException("CmdShell should be active for commands '" + commands
                    + SINGLE_QUOTE);
        }
    }

    /**
     * @param cmdShell
     * @param searchedLine
     * @param timeOutInMillis
     * @param maxLines
     * @param monitor
     * @return
     */
    private static List<String> getOutputUntil(IRemoteCommandShell cmdShell, String searchedLine,
            long timeOutInMillis, int maxLines, IProgressMonitor monitor) {

        LinkedList<String> lines = new LinkedList<String>();
        if (cmdShell == null || searchedLine == null) {
            return lines;
        }

        boolean finished = false;
        int firstIndex = 0;
        long lastChangedOutputTime = System.currentTimeMillis();
        boolean error = false;
        while (!finished && !monitor.isCanceled()) {
            // TODO: would be much easy if I would have a Stream instead of cmdShell.listOutput()
            Object[] listOutput = cmdShell.listOutput();
            // TODO: here is a possibility that rows were inserted into _output between call of
            // listOutput() and removeOutput().
            if (listOutput.length > maxLines) {
                cmdShell.removeOutput();
                firstIndex = 0;
            }
            for (int i = firstIndex; i < listOutput.length; i++) {
                if(monitor.isCanceled()){
                    break;
                }
                if ((listOutput[i] instanceof IRemoteError)) {
                    error = true;
                }
                if (!(listOutput[i] instanceof IRemoteOutput)) {
                    throw new FileSyncException("not IRemoteOutput: " + listOutput[i].getClass());
                }
                IRemoteOutput line = (IRemoteOutput) listOutput[i];
                String text = line.getText();
                if (text.equals(ERROR_SIGNAL)) {
                    error = true;
                }
                if (searchedLine.equals(text)) {
                    finished = true;
                    break;
                }
                lines.add(text);
                if (lines.size() > maxLines) {
                    lines.removeFirst();
                }
            }
            try {
                // Wait even if finished
                Thread.sleep(100);
            } catch (InterruptedException e) {
                /* nothing */
            }
            if (firstIndex != listOutput.length) {
                lastChangedOutputTime = System.currentTimeMillis();
            } else {
                if (NO_TIME_OUT_IN_MILLIS != timeOutInMillis
                        && System.currentTimeMillis() - lastChangedOutputTime > timeOutInMillis) {
                    Exception writerException = null;
                    File tmpFile = null;
                    try {
                        tmpFile = File.createTempFile(BulkSyncWizard.FILE_PREFIX
                                + "RseUtilsGetOutputUntil", ".stdout.txt");
                        RseSimpleUtils.write(new FileOutputStream(tmpFile), lines,
                                FS.CLOSE_WHEN_DONE);
                    } catch (Exception e) {
                        writerException = e;
                    }
                    throw new FileSyncException("shell timeout in Millis:" + timeOutInMillis
                            + ". Maybe more details in '"
                            + (tmpFile == null ? "" : tmpFile.toString()) + SINGLE_QUOTE,
                            writerException);
                }
            }
            firstIndex = listOutput.length;
        }
        if (error) {
            Exception writerException = null;
            File tmpFile = null;
            try {
                tmpFile = File.createTempFile(
                        BulkSyncWizard.FILE_PREFIX + "RseUtilsGetOutputUntil", ".stdout.txt");
                RseSimpleUtils.write(new FileOutputStream(tmpFile), lines, FS.CLOSE_WHEN_DONE);
            } catch (IOException e) {
                writerException = e;
            }
            throw new FileSyncException("Error during execution of remoteCmdShell '"
                    + cmdShell.getCommandSubSystem().getHost().toString()
                    + "'. Maybe more details in '" + (tmpFile == null ? "" : tmpFile.toString())
                    + SINGLE_QUOTE, writerException);
        }
        return lines;
    }
}
