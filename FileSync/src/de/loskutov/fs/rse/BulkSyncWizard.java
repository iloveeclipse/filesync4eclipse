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
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.ui.RSEUIPlugin;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.builder.ProjectProperties;
import de.loskutov.fs.builder.SyncWizard;
import de.loskutov.fs.command.CopyDelegate;
import de.loskutov.fs.command.FS;
import de.loskutov.fs.command.FileMapping;
import de.loskutov.fs.rse.utils.RseSimpleUtils;
import de.loskutov.fs.rse.utils.RseUtils;
import de.loskutov.fs.rse.utils.ZipUtils;
import de.loskutov.fs.rse.utils.ZipUtils.FileRecord;
import de.loskutov.fs.utils.DefaultPathHelper;

public class BulkSyncWizard extends SyncWizard {

    private static final boolean DEBUG = false;
    private static final boolean IS_FILE = true;
    public static final String FILE_PREFIX = "fileSync";

    static {
        // http://www.eclipse.org/forums/index.php?t=msg&goto=32205&
        RSEUIPlugin.getDefault();
        try {
            RSECorePlugin.waitForInitCompletion();
        } catch (InterruptedException e) {
            FileSyncPlugin.log("Rse init failed:", e, IStatus.WARNING);
        }
    }

    private final Map<IPath, Set<FileRecord>> sourceFiles;
    private final Map<IPath, Set<DeleteFileRecord>> filesToDelete;
    protected boolean delayedCopyDelete;


    /**
     * Used via reflection
     */
    public BulkSyncWizard() {
        super();
        sourceFiles = new HashMap<IPath, Set<FileRecord>>();
        filesToDelete = new HashMap<IPath, Set<DeleteFileRecord>>();
    }

    @Override
    public void setProjectProps(ProjectProperties props) throws IllegalArgumentException {
        super.setProjectProps(props);
        IEclipsePreferences preferences = props.getPreferences(false);
        delayedCopyDelete = preferences.getBoolean(ProjectProperties.KEY_DELAYED_COPY_DELETE,
                true);

    }

    @Override
    public boolean begin() {
        sourceFiles.clear();
        filesToDelete.clear();

        if (isDelayedCopyDelete() && isWindowsUncIssue(rootPath)) {
            FileSyncPlugin.log("Delayed-FileSync-Option for project '"
                    + projectProps.getProject().getName()
                    + "' ignored. @see http://support.microsoft.com/kb/156276/en for details.",
                    null, IStatus.INFO);
        }
        boolean globalValid = true;
        if (DefaultPathHelper.getPathHelper().isRseUnc(rootPath)) {
            logUncRseMsg(rootPath);
        }

        for (int i = 0; i < mappings.length; i++) {
            FileMapping fm = mappings[i];
            IPath destinationPath = getDestinationRootPath(fm);
            if (!destinationPath.equals(rootPath)) {
                if (isDelayedCopyDelete() && isWindowsUncIssue(destinationPath)) {
                    FileSyncPlugin
                    .log(
                            "Delayed-FileSync-Option for project '"
                            + projectProps.getProject().getName()
                            + "' and FileMapping '"
                            + fm
                            + "'ignored. @see http://support.microsoft.com/kb/156276/en for details.",
                            null, IStatus.INFO);
                }
                if (DefaultPathHelper.getPathHelper().isRseUnc(destinationPath)) {
                    logUncRseMsg(destinationPath);
                    fm.setValid(false);

                }
            } else if (!globalValid) {
                fm.setValid(globalValid);
            }
        }
        return super.begin();
    }

    private void logUncRseMsg(IPath destinationPath) {
        String msg = "UNC via RSE is not supported yet (Path '"
            + DefaultPathHelper.getPathHelper().toFqString(
                    destinationPath) + "') in Project '"
                    + projectProps.getProject().getName() + "'.";
        FileSyncPlugin.log(msg, null, IStatus.WARNING);
    }

    @Override
    public boolean commit() {

        boolean deleteOk = true;
        for (Iterator<Entry<IPath, Set<DeleteFileRecord>>> iterator = filesToDelete.entrySet().iterator(); iterator.hasNext();) {
            Entry<IPath, Set<DeleteFileRecord>> e = iterator.next();
            IPath destinationRoot = e.getKey();
            Set<DeleteFileRecord> filesToDeleteSet = e.getValue();
            deleteOk = commitDeletes(destinationRoot, filesToDeleteSet) && deleteOk;
        }

        boolean copyOk = deleteOk;
        for (Iterator<Entry<IPath, Set<FileRecord>>> iterator = sourceFiles.entrySet().iterator(); iterator.hasNext();) {
            Entry<IPath, Set<FileRecord>> e = iterator.next();
            IPath destinationRoot = e.getKey();
            Set<FileRecord> sourceFilesSet = e.getValue();
            copyOk = commitFiles(destinationRoot, sourceFilesSet) && copyOk;
        }
        return copyOk;
    }

    private boolean commitDeletes(IPath destinationRoot, Set<DeleteFileRecord> filesToDeleteSet) {
        if (filesToDeleteSet.size() == 0) {
            return true;
        }
        CmdExecuter cmdExecuter = getCmdExecuter(destinationRoot);


        try {

            String delPrefix = FILE_PREFIX + "filesToDeleteSet";
            String delSuffix = cmdExecuter.getFilesToDeleteSuffix();
            String randomName = RseSimpleUtils.getRandomName(delPrefix, delSuffix);

            File destinationFile = getDestinationFile(destinationRoot.append(randomName));
            if (!FS.create(destinationFile, IS_FILE)) {
                return false;
            }

            OutputStream out = RseUtils.getOutputStream(destinationFile);
            RseSimpleUtils.write(out, cmdExecuter.toStringsForDelete(filesToDeleteSet), cmdExecuter
                    .getLineSeparator(), FS.CLOSE_WHEN_DONE);

            if (DEBUG) {

                File tmpFile = File.createTempFile(delPrefix, delSuffix);
                tmpFile.deleteOnExit();

                FileOutputStream outF = new FileOutputStream(tmpFile);
                RseSimpleUtils.write(outF, cmdExecuter.toStringsForDelete(filesToDeleteSet), cmdExecuter
                        .getLineSeparator(), FS.CLOSE_WHEN_DONE);
                FileSyncPlugin.log(tmpFile + " created for Debug-Delete-Output for destination: '"
                        + destinationRoot.toString() + "'", null, IStatus.OK);
            }

            boolean executed = cmdExecuter.execute(cmdExecuter.getDeleteCommands(destinationFile));

            boolean result = FS.delete(destinationFile, false);
            if (!result && destinationFile.isFile()) {
                FileSyncPlugin.log("Failed to delete the workingZipFile '" + destinationFile
                        + "', for destination '" + destinationRoot + "'", null, IStatus.WARNING);
            }

            return result && executed;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private boolean commitFiles(IPath destinationRoot, Set<FileRecord> sourceFilesSet) {
        try {

            if (sourceFilesSet.size() == 0) {
                return true;
            }

            String zipPrefix = FILE_PREFIX + "zipForFileSync";
            String zipSuffix = ".zip";
            File destinationFile = getDestinationFile(destinationRoot.append(RseSimpleUtils
                    .getRandomName(zipPrefix, zipSuffix)));

            boolean created = FS.create(destinationFile, IS_FILE);
            FS.create(destinationFile, IS_FILE);

            if (!created) {
                FileSyncPlugin
                .log("Failed to create new external resource '" + destinationFile
                        + "', for destinationRoot '" + destinationRoot + "'", null,
                        IStatus.WARNING);
                return created;
            }

            OutputStream out = RseUtils.getOutputStream(destinationFile);
            FileRecord[] files = sourceFilesSet.toArray(new FileRecord[sourceFilesSet.size()]);
            ZipUtils.zipStream(out, files, useCurrentDateForDestinationFiles);

            if (DEBUG) {
                File tmpFile = File.createTempFile(zipPrefix, zipSuffix);
                tmpFile.deleteOnExit();
                ZipUtils.zipStream(new FileOutputStream(tmpFile), files,
                        useCurrentDateForDestinationFiles);
                FileSyncPlugin.log(tmpFile + " created for Debug-Zip-Output for destination: '"
                        + destinationRoot.toString() + "' and project '"
                        + projectProps.getProject().getName() + "'", null, IStatus.OK);
            }
            CmdExecuter cmdExecuter = getCmdExecuter(destinationRoot);
            boolean executedSuccessful = cmdExecuter.execute(cmdExecuter
                    .getUnzipCommands(destinationFile));

            boolean result = FS.delete(destinationFile, false);
            if (!result && destinationFile.isFile()) {
                FileSyncPlugin.log("Failed to delete the workingZipFile '" + destinationFile
                        + "', for destination '" + destinationRoot + "' and project '"
                        + projectProps.getProject().getName() + "'", null, IStatus.WARNING);
            }

            return result && executedSuccessful;
        } catch (Exception e) {
            FileSyncPlugin.log("failed to commit files for project '"
                    + projectProps.getProject().getName() + "'", e, IStatus.WARNING);
            return false;
        }
    }

    @Override
    protected boolean delete(IResource sourceRoot, boolean clean, IProgressMonitor monitor) {
        List<FileMapping> mappingList = getMappings(sourceRoot.getProjectRelativePath(),
                sourceRoot.getType() == IResource.FOLDER, false);

        if (mappingList == null) {
            return true;
        }
        File rootFile = rootPath == null ? null : rootPath.toFile();

        boolean commonState = true;
        for (int i = 0; i < mappingList.size(); i++) {
            FileMapping fm = mappingList.get(i);
            IPath relativePath = getRelativePath(sourceRoot, fm);
            if (isDelayedCopyDelete(fm)) {
                Set<DeleteFileRecord> deleteFilesSet = getDeleteFilesSet(getDestinationRootPath(fm));
                deleteFilesSet.add(new DeleteFileRecord(getDestinationFile(
                        sourceRoot, relativePath, fm).getAbsolutePath(), isContainer(sourceRoot)))
                        ;
            } else {
                commonState = delete(sourceRoot, rootFile, relativePath, fm, clean, monitor)
                && commonState;
                if(commonState) {
                    deleteParent(sourceRoot, clean, monitor, fm, rootFile, relativePath);
                }
            }
        }

        return commonState;
    }

    @Override
    protected boolean copy(IResource sourceRoot, IProgressMonitor monitor) {

        List<FileMapping> mappingList = getMappings(sourceRoot.getProjectRelativePath(),
                sourceRoot.getType() == IResource.FOLDER, false);

        if (mappingList == null) {
            return true;
        }

        boolean commonState = true;
        File sourceFile = getSourceFile(sourceRoot);
        for (int i = 0; i < mappingList.size(); i++) {
            FileMapping fm = mappingList.get(i);
            IPath relativePath = getRelativePath(sourceRoot, fm);
            if (isDelayedCopyDelete(fm)) {
                if (!isContainer(sourceRoot)) {
                    commonState = addSourceFile(fm, relativePath, sourceRoot, sourceFile)
                    && commonState;
                }
            } else {
                commonState = copy(sourceRoot, sourceFile, relativePath, fm, monitor)
                && commonState;
            }
        }

        if (monitor.isCanceled()) {
            FileSyncPlugin.log("Cancelled by user, failed to copy *all* resources, "
                    + "mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
        }

        return true;
    }

    private boolean addSourceFile(FileMapping fm, IPath relativePath, IResource sourceRoot,
            File sourceFile) {
        CopyDelegate cd = null;
        if (isUseCopyDelegate((IFile) sourceRoot, fm)) {
            cd = initCopyDelegate(createCopyDelegate(), (IFile) sourceRoot, fm);
        }
        IPath destinationRoot = getDestinationRootPath(fm);
        Set<FileRecord> sourceFilesSet = getSourceFilesSet(destinationRoot);
        return sourceFilesSet.add(new FileRecord(sourceFile, relativePath.toString())
        .setCopyDelegate(cd));
    }

    private Set<FileRecord> getSourceFilesSet(IPath destinationRoot) {
        Set<FileRecord> sourceFilesSet = sourceFiles.get(destinationRoot);
        if (sourceFilesSet == null) {
            sourceFilesSet = new TreeSet<FileRecord>();
            sourceFiles.put(destinationRoot, sourceFilesSet);
        }
        return sourceFilesSet;
    }

    private Set<DeleteFileRecord> getDeleteFilesSet(IPath destinationRoot) {
        Set<DeleteFileRecord> filesToDeleteSet = filesToDelete.get(destinationRoot);
        if (filesToDeleteSet == null) {
            filesToDeleteSet = new TreeSet<DeleteFileRecord>(Collections.reverseOrder());
            filesToDelete.put(destinationRoot, filesToDeleteSet);
        }
        return filesToDeleteSet;
    }

    public CmdExecuter getCmdExecuter(IPath destinationRoot) {
        return CmdExecuterFactory.getInstance().getCmdExecuter(destinationRoot);
    }

    /**
     * @param fm
     * @return delayedCopyDelete
     */
    public boolean isDelayedCopyDelete(FileMapping fm) {
        if (isWindowsUncIssue(getDestinationRootPath(fm))) {
            return false;
        }
        return isDelayedCopyDelete();
    }

    /**
     * @see <a href="http://support.microsoft.com/kb/156276/en">issue 156276</a>
     * @param path
     * @return
     */
    public boolean isWindowsUncIssue(IPath path) {
        return path != null && path.isUNC() && FS.isWin32();

    }

    public boolean isDelayedCopyDelete() {
        return delayedCopyDelete;
    }

    public static class DeleteFileRecord implements Comparable<DeleteFileRecord>{

        private final String targetName;
        private final boolean directory;

        public String getTargetName() {
            return targetName;
        }

        public DeleteFileRecord(String targetName, boolean directory){
            this.targetName = targetName;
            this.directory = directory;
        }


        public boolean isDirectory() {
            return directory;
        }

        public int compareTo(DeleteFileRecord o) {
            return targetName.compareTo(o.targetName);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            DeleteFileRecord other = (DeleteFileRecord) obj;
            if (targetName == null) {
                if (other.targetName != null) {
                    return false;
                }
            } else if (!targetName.equals(other.targetName)) {
                return false;
            }
            return true;
        }

    }
}
