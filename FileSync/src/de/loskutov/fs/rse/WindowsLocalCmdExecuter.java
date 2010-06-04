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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.command.FS;
import de.loskutov.fs.rse.utils.RseSimpleUtils;
import de.loskutov.fs.rse.utils.RseSimpleUtils.RedirectInputStream;

/**
 * This CmdExecuter does not use the RemoteShell, to execute the commands.
 *
 * @author volker
 */
public class WindowsLocalCmdExecuter extends RseWindowsCmdExecuter {

    public WindowsLocalCmdExecuter(IRemoteFile workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public boolean execute(String[] commands) {
        for (int i = 0; i < commands.length; i++) {

            // ProcessBuilder builder = new ProcessBuilder( "cmd", "/c", commands[i] );
            // builder.directory( new File(workingDirectory.getAbsolutePath()) );
            try {
                Process p = Runtime.getRuntime().exec(commands[i],
                        RseSimpleUtils.toEnvArray(System.getenv()),
                        new File(workingDirectory.getAbsolutePath()));
                // p = builder.start();

                File tmpStdErr = File.createTempFile(BulkSyncWizard.FILE_PREFIX
                        + "WindowsLocalExecute", ".stderr");
                File tmpStdOut = File.createTempFile(BulkSyncWizard.FILE_PREFIX
                        + "WindowsLocalExecute", ".stdout");
                RedirectInputStream redirectErr = new RedirectInputStream(p.getErrorStream(),
                        new FileOutputStream(tmpStdErr), FS.CLOSE_WHEN_DONE);
                RedirectInputStream redirectIn = new RedirectInputStream(p.getInputStream(),
                        new FileOutputStream(tmpStdOut), FS.CLOSE_WHEN_DONE);

                // XXX should try to use jobs instead. Too many threads may cause OOME
                Thread t1 = new Thread(redirectErr);
                t1.start();
                Thread t2 = new Thread(redirectIn);
                t2.start();

                int errorCode = p.waitFor();
                t1.join();
                t2.join();
                if (errorCode == 0 && tmpStdErr.length() == 0) {
                    FS.delete(tmpStdErr, false);
                    FS.delete(tmpStdOut, false);
                } else {
                    FileSyncPlugin.log("exitCode " + errorCode + " != 0 or stderr of command '"
                            + commands[i] + "'. See details in '" + tmpStdErr.toString() + "' or '"
                            + tmpStdOut + "' ", null, IStatus.WARNING);
                    return false;
                }
                p.destroy();
            } catch (InterruptedException e) {
                FileSyncPlugin.log("Cancelled while executing command '" + commands[i] + "'", e,
                        IStatus.WARNING);
                return false;
            } catch (IOException e) {
                FileSyncPlugin.log("Error while executing command '" + commands[i] + "'", e,
                        IStatus.WARNING);
                return false;
            }
        }
        return true;
    }

    @Override
    public String[] getUnzipCommands(File zipFile) {
        String[] ret = new String[] { "cmd /c " + getUnzipCmd() + " " + quote(zipFile.getAbsolutePath()) };
        return ret;
    }

}
