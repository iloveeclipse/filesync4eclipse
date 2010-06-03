/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Andrei Loskutov - initial API and implementation
 *  Volker Wandmaker - refactoring
 *******************************************************************************/
package de.loskutov.fs.command;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IStatus;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.command.ZipUtils.AbstractCopyDelegate;
import de.loskutov.fs.command.ZipUtils.LocalFileInputStream;
import de.loskutov.fs.command.ZipUtils.LocalOutputStream;

/**
 * @author Coloma Escribano, Ignacio - initial idea and first implementation
 * @author Andrei - production ready code :)
 */
public class CopyDelegate extends AbstractCopyDelegate {

    private Map<Pattern, String> patternToValue;
    private Map<Pattern, String> patternToKey;

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

    /**
     * Single file copy operation with replacement of variables on the fly. Implementation reads and
     * writes line by line, so that bug files could be proceeded
     *
     * @param source
     *            - should be file only
     * @param destination
     *            - should be already created
     * @return true if source was successfully copied
     */
    protected boolean copyInternal(File source, File destination) {
        boolean success = true;
        try {
            LocalFileInputStream sourceStream = new LocalFileInputStream(source);
            LocalOutputStream targetStream = new LocalOutputStream(FileSyncPlugin.getDefault()
                    .getFsPathUtil().getOutputStream(destination), destination.toString());
            success = copyInternal(sourceStream, targetStream, DEFAULT_OPTIONS);
        } catch (FileNotFoundException e) {
            FileSyncPlugin.log("Could not create Streams", e, IStatus.WARNING);
            return false;
        }

        if (!useCurrentDateForDestinationFiles) {
            boolean modified = destination.setLastModified(source.lastModified());
            if (!modified && FS.enableLogging) {
                FileSyncPlugin.log("Could not update last modified stamp for file '" + destination
                        + "'", null, IStatus.WARNING);
            }
        } else {
            // should be updated by system I/O
        }
        return success;
    }

    public Properties getPropertiesMap() {
        return variablesMap;
    }

    public void setPropertiesMap(Properties propertiesMap) {
        if (getPropertiesMap() != propertiesMap) {
            variablesMap = propertiesMap;
            initPatterns();
        }
    }

    public void setUseCurrentDateForDestinationFiles(
            boolean useCurrentDateForDestinationFiles) {
        this.useCurrentDateForDestinationFiles = useCurrentDateForDestinationFiles;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    protected boolean copyInternal(InputStream source, OutputStream destination, int options) {
        LineReader reader = null;
        LineWriter writer = null;

        boolean success = true;
        try {
            // Open the file and then get a channel from the stream
            reader = new LineReader(source, encoding);
            writer = new LineWriter(destination, encoding);

            copyInternalOpened(reader, writer);
        } catch (IOException e) {
            if (FS.enableLogging) {
                FileSyncPlugin.log("Could not copy stream '" + source + "' to '" + destination
                        + "'", e, IStatus.WARNING);
            }
            success = false;
        } finally {
            // Always close input and output streams.
            if (reader != null && (CLOSE_READER_OPTION & options) == CLOSE_READER_OPTION) {
                try {
                    reader.close();
                } catch (IOException e) {
                    if (FS.enableLogging) {
                        FileSyncPlugin.log("Could not close stream '" + source + "'", e,
                                IStatus.WARNING);
                    }
                    success = false;
                }
            }
            if (writer != null && (CLOSE_WRITER_OPTION & options) == CLOSE_WRITER_OPTION) {
                try {
                    writer.close();
                } catch (IOException e) {
                    if (FS.enableLogging) {
                        FileSyncPlugin.log("Could not close stream '" + destination + "'", e,
                                IStatus.WARNING);
                    }
                    success = false;
                }
            }
        }
        return success;
    }

    private void copyInternalOpened(LineReader reader, LineWriter writer) throws IOException {
        String line = null;
        while ((line = reader.readLineToString()) != null) {
            for (Iterator<Pattern> i = patternToValue.keySet().iterator(); i.hasNext();) {
                Pattern pattern = i.next();
                if (line.indexOf(patternToKey.get(pattern)) < 0) {
                    continue;
                }
                String value = patternToValue.get(pattern);
                line = pattern.matcher(line).replaceAll(value);
            }
            writer.writeLine(line);
        }
        writer.flush();
    }

    private void initPatterns() {
        patternToValue = new HashMap<Pattern, String>();
        patternToKey = new HashMap<Pattern, String>();
        Set<?> keySet = variablesMap.keySet();
        for (Iterator<?> i = keySet.iterator(); i.hasNext();) {
            String key = (String) i.next();
            Pattern pattern = Pattern.compile("\\$\\{" + key + "\\}");
            patternToValue.put(pattern, (String) variablesMap.get(key));
            patternToKey.put(pattern, key);
        }
    }
}
