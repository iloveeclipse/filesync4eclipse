/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IStatus;

import de.loskutov.fs.FileSyncPlugin;

/**
 * @author Andrei
 */
public class CopyDelegate1 extends CopyDelegate {
    private Map<Pattern, String> patternToValue;
    private Map<Pattern, String> patternToKey;

    @Override
    public void setPropertiesMap(Properties propertiesMap) {
        if(getPropertiesMap() != propertiesMap) {
            super.setPropertiesMap(propertiesMap);
            initPatterns();
        }
    }

    /**
     * Single file copy operation with replacement of variables on the fly.
     * Implementation reads and writes line by line, so that bug files could be proceeded
     * @param source - should be file only
     * @param destination - should be already created
     * @return true if source was successfully copied
     */
    @Override
    protected boolean copyInternal(File source, File destination) {

        boolean success = true;
        LineReader reader = null;
        LineWriter writer = null;

        try {
            // Open the file and then get a channel from the stream
            reader = new LineReader(new FileInputStream(source), encoding);
            writer = new LineWriter(new FileOutputStream(destination), encoding);
            String line = null;
            while((line = reader.readLineToString()) != null){
                for (Iterator<Pattern> i = patternToValue.keySet().iterator(); i.hasNext();) {
                    Pattern pattern =  i.next();
                    if(line.indexOf(patternToKey.get(pattern)) < 0 ){
                        continue;
                    }
                    String value = patternToValue.get(pattern);
                    line = pattern.matcher(line).replaceAll(value);
                }
                writer.writeLine(line);
            }
            writer.flush();
        } catch (IOException e) {
            if (FS.enableLogging) {
                FileSyncPlugin.log("Could not copy file '" + source + "' to '"
                        + destination + "'", e, IStatus.WARNING);
            }
            success = false;
        } finally {
            // Always close input and output streams.
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    if (FS.enableLogging) {
                        FileSyncPlugin.log("Could not close file stream for file '"
                                + source + "'", e, IStatus.WARNING);
                    }
                    success = false;
                }
            }
            if (writer != null) {
                try {
                    writer.close();
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

    private void initPatterns() {
        patternToValue = new HashMap<Pattern, String>();
        patternToKey = new HashMap<Pattern, String>();
        Set<String> keySet = variablesMap.stringPropertyNames();
        for (String key : keySet) {
            Pattern pattern = Pattern.compile("\\$\\{" + key + "\\}");
            patternToValue.put(pattern, variablesMap.getProperty(key));
            patternToKey.put(pattern, key);
        }
    }


}
