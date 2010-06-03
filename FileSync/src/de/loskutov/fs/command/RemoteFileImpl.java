/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.command;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

/**
 * Nearly the same as {@link UniFilePlus}. Differences are in the Methods: {@link #delete()} ( this
 * implemention is according the Description of {@link File#delete()} {@link #isDirectory()} ( this
 * implemention is according the Description of {@link File#isDirectory()} {@link #isFile()} ( this
 * implemention is according the Description of {@link File#isFile()}
 *
 * <p>
 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=310172
 * Take a look into these methods to see the differences in detail.
 *
 * @author Volker
 */
public class RemoteFileImpl extends File {

    private static final long serialVersionUID = 1L;

    private final UniFilePlus remoteFile;

    public RemoteFileImpl(IRemoteFile remoteFile) {
        this(new UniFilePlus(remoteFile));
    }

    public RemoteFileImpl(UniFilePlus remoteFile) {
        super(remoteFile.getRemoteFile().getAbsolutePath());
        this.remoteFile = remoteFile;
    }

    //    public boolean canExecute() {
    //        return remoteFile.canExecute();
    //    }

    @Override
    public boolean canRead() {
        return remoteFile.canRead();
    }

    @Override
    public boolean canWrite() {
        return remoteFile.canWrite();
    }

    @Override
    public int compareTo(File pathname) {
        return remoteFile.compareTo(pathname);
    }

    @Override
    public boolean createNewFile() throws IOException {
        return remoteFile.createNewFile();
    }

    @Override
    public boolean delete() {
        if (isDirectory() && list().length > 0) {
            return false;
        }
        return remoteFile.delete();
    }

    @Override
    public void deleteOnExit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        return remoteFile.equals(obj);
    }

    @Override
    public boolean exists() {
        return remoteFile.exists();
    }

    @Override
    public File getAbsoluteFile() {
        return remoteFile.getAbsoluteFile();
    }

    @Override
    public String getAbsolutePath() {
        return remoteFile.getAbsolutePath();
    }

    @Override
    public File getCanonicalFile() {
        return remoteFile.getCanonicalFile();
    }

    @Override
    public String getCanonicalPath() {
        return remoteFile.getCanonicalPath();
    }

    @Override
    public String getName() {
        return remoteFile.getName();
    }

    @Override
    public String getParent() {
        return remoteFile.getParent();
    }

    @Override
    public File getParentFile() {
        return remoteFile.getParentFile();
    }

    @Override
    public String getPath() {
        return remoteFile.getPath();
    }

    public IRemoteFile getRemoteFile() {
        return remoteFile.getRemoteFile();
    }

    //    public long getTotalSpace() {
    //        return remoteFile.getTotalSpace();
    //    }

    //    public long getUsableSpace() {
    //        return remoteFile.getUsableSpace();
    //    }

    @Override
    public int hashCode() {
        return remoteFile.hashCode();
    }

    @Override
    public boolean isAbsolute() {
        return remoteFile.isAbsolute();
    }

    @Override
    public boolean isDirectory() {
        return exists() && remoteFile.isDirectory();
    }

    @Override
    public boolean isFile() {
        return exists() && remoteFile.isFile();
    }

    @Override
    public boolean isHidden() {
        return remoteFile.isHidden();
    }

    @Override
    public long lastModified() {
        return remoteFile.lastModified();
    }

    @Override
    public long length() {
        return remoteFile.length();
    }

    @Override
    public String[] list() {
        return remoteFile.list();
    }

    @Override
    public String[] list(FilenameFilter filter) {
        return remoteFile.list(filter);
    }

    @Override
    public File[] listFiles() {
        return remoteFile.listFiles();
    }

    @Override
    public File[] listFiles(FileFilter filter) {
        return remoteFile.listFiles(filter);
    }

    @Override
    public File[] listFiles(FilenameFilter filter) {
        return remoteFile.listFiles(filter);
    }

    @Override
    public boolean mkdir() {
        return remoteFile.mkdir();
    }

    @Override
    public boolean mkdirs() {
        return remoteFile.mkdirs();
    }

    @Override
    public boolean renameTo(File dest) {
        return remoteFile.renameTo(dest);
    }

    @Override
    public boolean setLastModified(long time) {
        return remoteFile.setLastModified(time);
    }

    @Override
    public boolean setReadOnly() {
        return remoteFile.setReadOnly();
    }

    @Override
    public String toString() {
        return remoteFile.toString();
    }

    @Override
    public URI toURI() {
        return remoteFile.toURI();
    }

    @Override
    public URL toURL() throws MalformedURLException {
        return remoteFile.toURL();
    }

}
