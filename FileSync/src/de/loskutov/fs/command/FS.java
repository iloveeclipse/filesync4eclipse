/*******************************************************************************
 * Copyright (c) 2009 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.eclipse.core.runtime.IStatus;

import de.loskutov.fs.FileSyncPlugin;

/**
 * Utility class for file system related operations.
 * @author Andrey
 */
public final class FS {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    // not final for tests only
    public static boolean enableLogging = true;


    private FS() {
        // don't instantiate me
    }

    public static boolean isWin32() {
        return OS_NAME.indexOf("windows") >= 0;
    }



    /**
     * Single file/directory create operation.
     * @param destination
     * @param isFile
     * @return true if source was successfully created or if it was already existing
     */
    public static boolean create(File destination, boolean isFile) {
        if (destination == null) {
            return true;
        }
        if (isFile && destination.isFile()) {
            return true;
        } else if (!isFile && destination.isDirectory()) {
            return true;
        }

        boolean result = false;

        if (isFile) {
            File dir = destination.getParentFile();
            if (!dir.exists()) {
                result = dir.mkdirs();
                if (!result) {
                    if (enableLogging) {
                        FileSyncPlugin.log("Could not create directory '" + dir + "'",
                                null, IStatus.WARNING);
                    }
                    return false;
                }
            }
            try {
                result = destination.createNewFile();
            } catch (IOException e) {
                if (enableLogging) {
                    FileSyncPlugin.log("Could not create file '" + destination + "'", e,
                            IStatus.WARNING);
                }
            }
        } else {
            result = destination.mkdirs();
            if (!result && enableLogging) {
                FileSyncPlugin.log("Could not create directory '" + destination + "'",
                        null, IStatus.WARNING);
            }
        }
        return result;
    }

    /**
     * If "recursive" is false, then this is a single file/directory delete
     * operation. Directory should be empty before it can be deleted.
     * If "recursive" is true, then all children will be deleted too.
     * @param source
     * @return true if source was successfully deleted or if it was not existing
     */
    public static boolean delete(File source, boolean recursive) {
        if (source == null || !source.exists()) {
            return true;
        }
        if (recursive) {
            if (source.isDirectory()) {
                File[] files = source.listFiles();
                boolean ok = true;
                for (int i = 0; i < files.length; i++) {
                    ok = delete(files[i], true);
                    if (!ok) {
                        return false;
                    }
                }
            } /* comented out because this kind of decisions is for SyncWizard only
             else {
             File dir = source.getParentFile();
             boolean ok = delete(source, false);
             if(ok && dir.list().length == 0){
             // delete parant directory, because it is now empty
             // and we have a clean build
             ok = delete(dir, false);
             }
             return ok;
             } */
        }
        boolean result = source.delete();
        if (!result && !source.isDirectory() && enableLogging) {
            FileSyncPlugin.log("Could not delete file '" + source + "'", null,
                    IStatus.WARNING);
        }
        return result;
    }

    /**
     * Single file copy operation.
     * @param source - should be file only
     * @param destination - should be already created
     * @param useCurrentDateForDestinationFiles To use current date for
     * destination files instead of the source file date
     * @return true if source was successfully copied
     */
    public static boolean copy(File source, File destination,
            boolean useCurrentDateForDestinationFiles) {
        if (source == null || destination == null || !source.exists()
                || !destination.exists() || source.isDirectory()
                || destination.isDirectory()) {
            if (enableLogging) {
                FileSyncPlugin.log("Could not copy file '" + source + "' to '"
                        + destination + "'", null, IStatus.WARNING);
            }
            return false;
        }

        /*
         * prevent from overhead on identical files - this works fine
         * <b>only</b> if source and destination are on the same partition (=> the
         * same filesystem). If both files are on different partitions, then
         * 1) the file size could differ because of different chunk size
         * 2) the file time could differ because of different timestamp
         * formats on different file systems (e.g. NTFS and FAT)
         */
        if (!useCurrentDateForDestinationFiles
                && destination.lastModified() == source.lastModified()
                && destination.length() == source.length()) {
            return true;
        }

        boolean success = true;
        FileInputStream fin = null; // Streams to the two files.
        FileOutputStream fout = null; // These are closed in the finally block.
        try {
            // Open a stream to the input file and get a channel from it
            fin = new FileInputStream(source);
            FileChannel in = fin.getChannel();

            // Now get the output channel
            FileChannel out;

            fout = new FileOutputStream(destination); // open file stream
            out = fout.getChannel(); // get its channel

            // Query the size of the input file
            long numbytes = in.size();

            // Bulk-transfer all bytes from one channel to the other.
            // This is a special feature of FileChannel channels.
            // See also FileChannel.transferFrom( )

            // TransferTo does not work under certain Linux kernel's
            // with java 1.4.2, see bug
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5056395
            // in.transferTo(0, numbytes, out);
            out.transferFrom(in, 0, numbytes);
        } catch (IOException e) {
            if (enableLogging) {
                FileSyncPlugin.log("Could not copy file '" + source + "' to '"
                        + destination + "'", e, IStatus.WARNING);
            }
            success = false;
        } finally {
            // Always close input and output streams. Doing this closes
            // the channels associated with them as well.

            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                    if (enableLogging) {
                        FileSyncPlugin.log("Could not close file stream for file '"
                                + source + "'", e, IStatus.WARNING);
                    }
                    success = false;
                }
            }
            if (fout != null) {
                try {
                    fout.close();
                    if (!useCurrentDateForDestinationFiles) {
                        boolean modified = destination.setLastModified(source.lastModified());
                        if(!modified && FS.enableLogging){
                            FileSyncPlugin.log("Could not update last modified stamp for file '"
                                    + destination + "'", null, IStatus.WARNING);
                        }
                    } else {
                        // should be updated by system I/O
                    }
                } catch (IOException e) {
                    if (enableLogging) {
                        FileSyncPlugin.log("Could not close file stream for file '"
                                + destination + "'", e, IStatus.WARNING);
                    }
                    success = false;
                }
            }

        }

        return success;
    }
}
