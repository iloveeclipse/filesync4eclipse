/*******************************************************************************
 * Copyright (c) 2005 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSD License
 * which accompanies this distribution, and is available at
 * http://www.opensource.org/licenses/bsd-license.php
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package test;

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
import de.loskutov.fs.command.CopyDelegate;
import de.loskutov.fs.command.FS;
import de.loskutov.fs.command.LineReader;
import de.loskutov.fs.command.LineWriter;

/**
 * @author Andrei
 */
public class CopyDelegate2 extends CopyDelegate {
    private Map/*<Pattern, String>*/ patternToValue;
    private Map/*<Pattern, String>*/ patternToKey;

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
    protected boolean copyInternal(File source, File destination) {

        boolean success = true;
        FileInputStream fin = null;
        FileOutputStream fout = null;

        try {
            // Open the file and then get a channel from the stream
            fin = new FileInputStream(source);
            fout = new FileOutputStream(destination);

            LineReader reader  = new LineReader(fin, encoding);
            LineWriter writer = new LineWriter(fout, encoding);
            String line = null;
            while((line = reader.readLineToString()) != null){
                for (Iterator i = patternToValue.keySet().iterator(); i.hasNext();) {
                    Pattern pattern =  (Pattern) i.next();
                    if(line.indexOf((String) patternToKey.get(pattern)) < 0 ){
                        continue;
                    }
                    String value = (String) patternToValue.get(pattern);
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
                        destination.setLastModified(source.lastModified());
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
        patternToValue = new HashMap();
        patternToKey = new HashMap();
        Set keySet = variablesMap.keySet();
        for (Iterator i = keySet.iterator(); i.hasNext();) {
            String key = (String) i.next();
            Pattern pattern = Pattern.compile("\\$\\{" + key + "\\}");
            patternToValue.put(pattern, variablesMap.get(key));
            patternToKey.put(pattern, key);
        }
    }


}
