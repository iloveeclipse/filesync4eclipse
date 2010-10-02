package de.loskutov.fs.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.core.runtime.IStatus;

import de.loskutov.fs.FileSyncPlugin;

/**
 * @author Coloma Escribano, Ignacio - initial idea and first implementation
 * @author Andrei - production ready code :)
 */
public class CopyDelegate {

    /**
     * true to use the current date for destination files,
     * instead of keeping the same date for source and destination
     */
    protected boolean useCurrentDateForDestinationFiles;

    /**
     * map of properties to perform the substitution.
     * Key is variable name, value is the value
     */
    protected Properties variablesMap;

    protected String encoding;

    public CopyDelegate() {
        super();
        //        setEncoding("ISO-8859-1");
    }


    /**
     * Single file copy operation with replacement of variables on the fly.
     * Implementation reads complete file into the memory
     * @param source - should be file only
     * @param destination - should be already created
     * @return true if source was successfully copied
     */
    public boolean copy(File source, File destination) {

        if (source == null || destination == null || !source.exists()
                || !destination.exists() || source.isDirectory()
                || destination.isDirectory()) {
            if (FS.enableLogging) {
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

        return copyInternal(source, destination);
    }


    protected boolean copyInternal(File source, File destination) {
        boolean success = true;
        FileInputStream fin = null;
        FileOutputStream fout = null;

        try {

            // Open the file and then get a channel from the stream
            fin = new FileInputStream(source);
            int size = (int) source.length();
            byte[] array = new byte[size];
            fin.read(array, 0, size);
            String string = new String(array, encoding);
            for (Object name : variablesMap.keySet()) {
                String key = (String) name;
                String value = (String) variablesMap.get(key);
                if(string.indexOf(key) >= 0) {
                    string = string.replaceAll("\\$\\{" + key + "\\}", value);
                }
            }

            // write the destination
            fout = new FileOutputStream(destination);
            fout.write(string.getBytes(encoding));
        } catch (IOException e) {
            if (FS.enableLogging) {
                FileSyncPlugin.log("Could not copy file '" + source + "' to '"
                        + destination + "'", e, IStatus.WARNING);
            }
            success = false;
        } finally {
            // Always close input and output streams.
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                    if (FS.enableLogging) {
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
                    if (FS.enableLogging) {
                        FileSyncPlugin.log("Could not close file stream for file '"
                                + destination + "'", e, IStatus.WARNING);
                    }
                    success = false;
                }
            }
        }
        return success;
    }

    public Properties getPropertiesMap() {
        return variablesMap;
    }

    public void setPropertiesMap(Properties propertiesMap) {
        this.variablesMap = propertiesMap;
    }

    public boolean isUseCurrentDateForDestinationFiles() {
        return useCurrentDateForDestinationFiles;
    }

    public void setUseCurrentDateForDestinationFiles(
            boolean useCurrentDateForDestinationFiles) {
        this.useCurrentDateForDestinationFiles = useCurrentDateForDestinationFiles;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }
}
