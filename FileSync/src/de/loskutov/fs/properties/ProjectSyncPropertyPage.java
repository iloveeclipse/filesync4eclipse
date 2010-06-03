/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Andrei Loskutov - initial API and implementation
 * 	Volker Wandmaker - added delayedCopyDeleteField
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.io.File;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.dialogs.NewFolderDialog;
import org.eclipse.ui.dialogs.PropertyPage;
import org.eclipse.ui.model.BaseWorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;
import org.osgi.service.prefs.BackingStoreException;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.builder.CharOperation;
import de.loskutov.fs.builder.FileSyncBuilder;
import de.loskutov.fs.command.FileMapping;
import de.loskutov.fs.command.FileSyncException;
import de.loskutov.fs.command.PathVariableHelper;
import de.loskutov.fs.dialogs.DialogField;
import de.loskutov.fs.dialogs.IDialogFieldListener;
import de.loskutov.fs.dialogs.IStatusChangeListener;
import de.loskutov.fs.dialogs.IStringButtonAdapter;
import de.loskutov.fs.dialogs.ITreeListAdapter;
import de.loskutov.fs.dialogs.LayoutUtil;
import de.loskutov.fs.dialogs.MultipleFolderSelectionDialog;
import de.loskutov.fs.dialogs.PixelConverter;
import de.loskutov.fs.dialogs.SelectionButtonDialogField;
import de.loskutov.fs.dialogs.StatusInfo;
import de.loskutov.fs.dialogs.StringButtonDialogField;
import de.loskutov.fs.dialogs.StringDialogField;
import de.loskutov.fs.dialogs.TreeListDialogField;
import de.loskutov.fs.dialogs.TypedElementSelectionValidator;
import de.loskutov.fs.dialogs.TypedViewerFilter;

public class ProjectSyncPropertyPage extends PropertyPage implements IStatusChangeListener {

    protected IStatus errorStatus = new StatusInfo(IStatus.ERROR, "Please select one file");
    protected IStatus okStatus = new StatusInfo();

    /*
     * OpenExternalFileAction -> external file dialog jdt ExclusionInclusionDialog -> select dialog
     * relative to parent resource
     */
    protected IWorkspaceRoot workspaceRoot;

    protected List<PathListElement> mappingList;

    protected StringButtonDialogField destPathDialogField;

    protected TreeListDialogField foldersList;

    protected StringDialogField outputLocationField;

    protected SelectionButtonDialogField useFolderOutputsField;

    private StatusInfo destFolderStatus;

    private IProject project;

    private IPath defDestinationPath;

    private final static int IDX_ADD = 0;

    private final static int IDX_EDIT = 2;

    private final static int IDX_REMOVE = 3;

    private final PathListElementComparator pathComparator;

    private String oldMappings;

    protected SelectionButtonDialogField useCurrentDateField;
    protected SelectionButtonDialogField includeTeamFilesField;
    protected SelectionButtonDialogField delayedCopyDeleteField;

    private SelectionButtonDialogField enableFileSyncField;

    private PathVariableHelper pathVariableHelper;

    protected StringButtonDialogField variablesDialogField;

    private final IValueCallback defVariablesCallback;

    private final IValueCallback defPathCallback;

    /**
     * Constructor for SamplePropertyPage.
     */
    public ProjectSyncPropertyPage() {
        super();
        pathComparator = new PathListElementComparator();

        defVariablesCallback = new IValueCallback() {

            public Object getValue() {
                return getDefaultVariablesPath();
            }
        };
        defPathCallback = new IValueCallback() {

            public Object getValue() {
                return getDefaultDestinationPath();
            }
        };
    }

    protected static Composite createContainer(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        composite.setLayout(layout);
        GridData gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
                | GridData.HORIZONTAL_ALIGN_FILL);
        gridData.grabExcessHorizontalSpace = true;
        composite.setLayoutData(gridData);
        return composite;
    }

    /**
     * @see PreferencePage#createContents(Composite)
     */
    protected Control createContents(Composite parent) {
        TabFolder tabFolder = new TabFolder(parent, SWT.TOP);
        tabFolder.setLayout(new GridLayout(1, true));
        tabFolder.setLayoutData(new GridData(GridData.FILL_BOTH));

        TabItem tabFilter = new TabItem(tabFolder, SWT.NONE);
        tabFilter.setText("Source and Target Configuration");

        TabItem support = new TabItem(tabFolder, SWT.NONE);
        support.setText("Misc...");
        Composite supportPanel = createContainer(tabFolder);
        support.setControl(supportPanel);
        SupportPanel.createSupportLinks(supportPanel);


        // ensure the page has no special buttons
        noDefaultAndApplyButton();

        mappingList = new ArrayList<PathListElement>();

        workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

        BuildPathAdapter adapter = new BuildPathAdapter();

        destPathDialogField = new StringButtonDialogField(adapter);
        destPathDialogField.setButtonLabel("Browse...");
        destPathDialogField.setDialogFieldListener(adapter);
        destPathDialogField.setLabelText("Default target folder:");

        variablesDialogField = new StringButtonDialogField(adapter);
        variablesDialogField.setButtonLabel("Browse...");
        variablesDialogField.setDialogFieldListener(adapter);
        variablesDialogField.setLabelText("Default variables file:");

        destFolderStatus = new StatusInfo();

        project = (IProject) getElement();
        pathVariableHelper = new PathVariableHelper();

        ProjectProperties properties = ProjectProperties.getInstance(project);
        List listeners = properties.getProjectPreferenceChangeListeners();
        boolean noBuilderInstalled = listeners.isEmpty();

        IEclipsePreferences preferences = getPreferences(noBuilderInstalled);
        IPath destPath = null;
        IPath variables = null;
        try {
            String defDest = preferences.get(ProjectProperties.KEY_DEFAULT_DESTINATION, "");
            IPath projectPath = project.getLocation();

            destPath = pathVariableHelper.resolveVariable(defDest, projectPath);
            variables = readVariablesPath(preferences);
        } catch (IllegalStateException e) {
            FileSyncPlugin.log("FileSync project preferences (for project '" + project.getName()
                    + "') error: " + e.getMessage(), e, IStatus.ERROR);
        }

        init(destPath, variables, properties.getMappings());

        PixelConverter converter = new PixelConverter(tabFolder);

        Composite composite = new Composite(tabFolder, SWT.NONE);
        tabFilter.setControl(composite);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.numColumns = 1;
        composite.setLayout(layout);

        Composite folder = new Composite(composite, SWT.NONE);
        layout = new GridLayout();
        folder.setLayout(layout);
        folder.setLayoutData(new GridData(GridData.FILL_BOTH));

        initSyncPage();
        initSyncControl(folder);

        Composite editorcomp = new Composite(composite, SWT.NONE);

        DialogField[] editors = new DialogField[] { destPathDialogField };
        LayoutUtil.doDefaultLayout(editorcomp, editors, noBuilderInstalled, 0, 0);

        int maxFieldWidth = converter.convertWidthInCharsToPixels(40);
        LayoutUtil.setWidthHint(destPathDialogField.getTextControl(null), maxFieldWidth);
        LayoutUtil.setHorizontalGrabbing(destPathDialogField.getTextControl(null));

        DialogField[] editors2 = new DialogField[] { variablesDialogField };
        LayoutUtil.doDefaultLayout(editorcomp, editors2, noBuilderInstalled, 0, 0);

        maxFieldWidth = converter.convertWidthInCharsToPixels(40);
        LayoutUtil.setWidthHint(variablesDialogField.getTextControl(null), maxFieldWidth);
        LayoutUtil.setHorizontalGrabbing(variablesDialogField.getTextControl(null));

        editorcomp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        statusChanged(destFolderStatus);
        Dialog.applyDialogFont(composite);

        if (Display.getCurrent() != null) {
            updateUI();
        } else {
            Display.getDefault().asyncExec(new Runnable() {

                public void run() {
                    updateUI();
                }
            });
        }
        return composite;
    }

    public void initSyncPage() {

        PathContainerAdapter adapter = new PathContainerAdapter();

        boolean disabled = !ProjectHelper.hasBuilder(project)
        || ProjectHelper.isBuilderDisabled(project);
        enableFileSyncField = new SelectionButtonDialogField(SWT.CHECK);
        enableFileSyncField.setSelection(!disabled);
        enableFileSyncField.setLabelText("Enable FileSync builder for project");
        enableFileSyncField.setDialogFieldListener(adapter);

        String[] buttonLabels;

        buttonLabels = new String[] {
                /* 0 = IDX_ADDEXIST */"&Add Folder...",
                /* 1 */null,
                /* 2 = IDX_EDIT */"Edit...",
        /* 3 = IDX_REMOVE */"Remove"};

        foldersList = new TreeListDialogField(adapter, buttonLabels, new PathListLabelProvider());
        foldersList.setDialogFieldListener(adapter);
        foldersList.setLabelText("Available synchronization mappings:");

        /*
         * the small hack to have all entries sorted in alphab. order except of "include/exclude"
         * branches - they should be inversed
         */
        foldersList.setViewerSorter(new ViewerSorter(new Collator() {

            private final Collator delegate = Collator.getInstance();

            public int compare(String source, String target) {
                return PathListLabelProvider.compare(source, target);
            }

            public CollationKey getCollationKey(String source) {
                return delegate.getCollationKey(source);
            }

            public int hashCode() {
                return delegate.hashCode();
            }
        }));
        foldersList.enableButton(IDX_EDIT, false);

        useFolderOutputsField = new SelectionButtonDialogField(SWT.CHECK);
        useFolderOutputsField.setSelection(false);
        useFolderOutputsField.setLabelText("Allow different target folders");
        useFolderOutputsField.setDialogFieldListener(adapter);

        // useVariablesField = new SelectionButtonDialogField(SWT.CHECK);
        // useVariablesField.setSelection(false);
        // useVariablesField.setLabelText("Allow different variables files");
        // useVariablesField.setDialogFieldListener(adapter);

        useCurrentDateField = new SelectionButtonDialogField(SWT.CHECK);
        useCurrentDateField.setSelection(false);
        useCurrentDateField.setLabelText("Use current date for destination files");
        useCurrentDateField.setDialogFieldListener(adapter);

        includeTeamFilesField = new SelectionButtonDialogField(SWT.CHECK);
        includeTeamFilesField.setSelection(false);
        includeTeamFilesField.setLabelText("Sync team private files (like .svn)");
        includeTeamFilesField.setDialogFieldListener(adapter);

        delayedCopyDeleteField = new SelectionButtonDialogField(SWT.CHECK);
        delayedCopyDeleteField.setSelection(FileSyncPlugin.getDefault().isDefaultDelayedCopy());
        delayedCopyDeleteField
        .setLabelText("delayed copy/delete (faster, especially on slow remote-connections)");
        delayedCopyDeleteField.setDialogFieldListener(adapter);

        if (!FileSyncPlugin.getDefault().isRseAvailable()) {
            delayedCopyDeleteField.setToolTipText(FileSyncPlugin.getDefault().getRseRequirement()
                    + " not available.");
        } else {
            delayedCopyDeleteField
            .setToolTipText("collects the sync-information first, sends it compressed to the target system and execute it their.");
        }

        enableInputControls(!disabled);
    }

    protected void init() {
        boolean useFolderOutputs = hasDifferentOutputFolders();
        // boolean useVariables = hasDifferentVasriables();
        ArrayList<PathListElement> folders = new ArrayList<PathListElement>();
        for (int i = 0; i < mappingList.size(); i++) {
            PathListElement cpe = mappingList.get(i);
            folders.add(cpe);
        }

        foldersList.setElements(folders);
        useFolderOutputsField.setSelection(useFolderOutputs);
        // useVariablesField.setSelection(useVariables);

        for (int i = 0; i < folders.size(); i++) {
            PathListElement cpe = folders.get(i);
            IPath[] patterns = (IPath[]) cpe.getAttribute(PathListElement.EXCLUSION);
            boolean hasOutputFolder = (cpe.getAttribute(PathListElement.DESTINATION) != null);
            if (patterns.length > 0 || hasOutputFolder) {
                foldersList.expandElement(cpe, 3);
            }
        }

        IEclipsePreferences preferences = getPreferences(false);
        boolean useCurrentDate = preferences.getBoolean(ProjectProperties.KEY_USE_CURRENT_DATE,
                false);

        useCurrentDateField.setSelection(useCurrentDate);

        boolean includeTeamFiles = preferences.getBoolean(
                ProjectProperties.KEY_INCLUDE_TEAM_PRIVATE, false);

        includeTeamFilesField.setSelection(includeTeamFiles);

        boolean delayedCopyDelete = preferences.getBoolean(
                ProjectProperties.KEY_DELAYED_COPY_DELETE, FileSyncPlugin.getDefault()
                .isDefaultDelayedCopy());

        delayedCopyDeleteField.setSelection(delayedCopyDelete);
    }

    private IPath readVariablesPath(IEclipsePreferences preferences) {
        IPath variables = null;
        String vars = preferences.get(ProjectProperties.KEY_DEFAULT_VARIABLES, null);
        if (vars != null && vars.trim().length() > 0) {
            variables = FileMapping.getRelativePath(new Path(vars), project.getFullPath());
            if (variables == null) {
                FileSyncPlugin.log("Path is not relative and will be ignored: " + vars, null,
                        IStatus.ERROR);
            }
        }
        return variables;
    }

    /**
     * @return true, if any one of existing path mappings uses different output folder as default
     *         one
     */
    protected boolean hasDifferentOutputFolders() {
        if (mappingList == null) {
            return false;
        }
        for (int i = 0; i < mappingList.size(); i++) {
            PathListElement cpe = mappingList.get(i);
            boolean hasOutputFolder = (cpe.getAttribute(PathListElement.DESTINATION) != null);
            if (hasOutputFolder) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true, if any one of existing path mappings uses the default output folder
     */
    protected boolean usesDefaultOutputFolder() {
        if (mappingList == null) {
            return false;
        }
        for (int i = 0; i < mappingList.size(); i++) {
            PathListElement cpe = mappingList.get(i);
            Object dest = cpe.getAttribute(PathListElement.DESTINATION);
            boolean hasDefFolder = dest == null || dest.toString().trim().length() == 0;
            if (hasDefFolder) {
                return true;
            }
        }
        return false;
    }

    protected void initSyncControl(Composite parent) {
        PixelConverter converter = new PixelConverter(parent);
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));
        LayoutUtil.doDefaultLayout(composite, new DialogField[] { enableFileSyncField, foldersList,
                useFolderOutputsField, /* useVariablesField, */
                includeTeamFilesField, useCurrentDateField, delayedCopyDeleteField }, true,
                SWT.DEFAULT, SWT.DEFAULT);

        LayoutUtil.setHorizontalGrabbing(foldersList.getTreeControl(null));

        int buttonBarWidth = converter.convertWidthInCharsToPixels(24);
        foldersList.setButtonsMinWidth(buttonBarWidth);

        // expand
        List elements = foldersList.getElements();
        for (int i = 0; i < elements.size(); i++) {
            PathListElement elem = (PathListElement) elements.get(i);
            IPath[] exclusionPatterns = (IPath[]) elem.getAttribute(PathListElement.EXCLUSION);
            IPath[] inclusionPatterns = (IPath[]) elem.getAttribute(PathListElement.INCLUSION);
            IPath output = (IPath) elem.getAttribute(PathListElement.DESTINATION);
            if (exclusionPatterns.length > 0 || inclusionPatterns.length > 0 || output != null) {
                foldersList.expandElement(elem, 3);
            }
        }
    }

    protected void pathListKeyPressed(TreeListDialogField field, KeyEvent event) {
        if (field == foldersList) {
            if (event.character == SWT.DEL && event.stateMask == 0) {
                List selection = field.getSelectedElements();
                if (canRemove(selection)) {
                    removeEntry();
                }
            }
        }
    }

    protected void pathListDoubleClicked(TreeListDialogField field) {
        if (field == foldersList) {
            List selection = field.getSelectedElements();
            if (canEdit(selection)) {
                editEntry();
            }
        }
    }

    private boolean hasMembers(IContainer container) {
        try {
            IResource[] members = container.members();
            for (int i = 0; i < members.length; i++) {
                if (members[i] instanceof IContainer) {
                    return true;
                }
            }
        } catch (CoreException e) {
            // ignore
        }
        return false;
    }

    protected void pathListButtonPressed(DialogField field, int index) {
        if (field != foldersList) {
            return;
        }
        if (index == IDX_ADD) {
            addEntry();
        } else if (index == IDX_EDIT) {
            editEntry();
        } else if (index == IDX_REMOVE) {
            removeEntry();
        }
    }

    private void addEntry() {
        List<PathListElement> elementsToAdd = new ArrayList<PathListElement>(10);
        if (hasMembers(project)) {
            PathListElement[] srcentries = openFolderDialog(null);
            if (srcentries != null) {
                for (int i = 0; i < srcentries.length; i++) {
                    elementsToAdd.add(srcentries[i]);
                }
            }
        } else {
            boolean addRoot = MessageDialog.openQuestion(getShell(), "Project has no folders",
            "Current project has no folders. Create mapping for project root?");
            if (addRoot) {
                PathListElement entry = newFolderElement(project);
                elementsToAdd.add(entry);
            } else {
                PathListElement entry = openNewFolderDialog(null);
                if (entry != null) {
                    elementsToAdd.add(entry);
                }
            }
        }
        if (!elementsToAdd.isEmpty()) {

            HashSet modifiedElements = new HashSet();
            askForAddingExclusionPatternsDialog(elementsToAdd, modifiedElements);

            foldersList.addElements(elementsToAdd);
            foldersList.postSetSelection(new StructuredSelection(elementsToAdd));

            if (!modifiedElements.isEmpty()) {
                for (Iterator iter = modifiedElements.iterator(); iter.hasNext();) {
                    Object elem = iter.next();
                    foldersList.refresh(elem);
                    foldersList.expandElement(elem, 3);
                }
            }
            dialogFieldChanged(destPathDialogField);
        }
    }

    private void editEntry() {
        List selElements = foldersList.getSelectedElements();
        if (selElements.size() != 1) {
            return;
        }
        Object elem = selElements.get(0);
        if (foldersList.getIndexOfElement(elem) != -1) {
            editElementEntry((PathListElement) elem);
        } else if (elem instanceof PathListElementAttribute) {
            editAttributeEntry((PathListElementAttribute) elem);
        }
    }

    private void editElementEntry(PathListElement elem) {
        PathListElement res = null;

        res = openNewFolderDialog(elem);

        if (res != null) {
            foldersList.replaceElement(elem, res);
        }
    }

    private void editAttributeEntry(PathListElementAttribute elem) {
        String key = elem.getKey();
        if (key.equals(PathListElement.DESTINATION)) {
            IPath path = (IPath) elem.getValue();
            if (path == null) {
                path = getDefaultDestinationPath();
            }
            DirectoryDialog dialog = new DirectoryDialog(getShell());
            dialog.setMessage("Select target folder");
            if (path != null) {
                dialog.setFilterPath(path.toOSString());
            }
            String absPath = dialog.open();
            if (absPath != null) {
                elem.getParent().setAttribute(PathListElement.DESTINATION, new Path(absPath),
                        defPathCallback);
                foldersList.refresh();
            }
            dialogFieldChanged(destPathDialogField);
        } else if (key.equals(PathListElement.VARIABLES)) {
            IPath path = (IPath) elem.getValue();
            if (path == null) {
                path = getDefaultVariablesPath();
            }
            IPath destPath = openFileDialog(path);
            if (destPath != null) {
                elem.getParent().setAttribute(PathListElement.VARIABLES, destPath,
                        defVariablesCallback);
                foldersList.refresh();
            }
            // destinationPathDialogFieldChanged();
        } else if (key.equals(PathListElement.EXCLUSION)) {
            showExclusionInclusionDialog(elem.getParent(), true);
        } else if (key.equals(PathListElement.INCLUSION)) {
            showExclusionInclusionDialog(elem.getParent(), false);
        }
    }

    protected IPath getDefaultVariablesPath() {
        String text = variablesDialogField.getText();
        if ((text == null || text.trim().length() == 0)) {
            return null;
        }
        return FileMapping.getRelativePath(new Path(text), project.getFullPath());
    }

    private void showExclusionInclusionDialog(PathListElement selElement, boolean focusOnExclusion) {
        InclusionExclusionDialog dialog = new InclusionExclusionDialog(getShell(), selElement,
                focusOnExclusion);
        if (dialog.open() == Window.OK) {
            selElement.setAttribute(PathListElement.INCLUSION, dialog.getInclusionPattern(), null);
            selElement.setAttribute(PathListElement.EXCLUSION, dialog.getExclusionPattern(), null);
            foldersList.refresh();
            // patternListCheckField.dialogFieldChanged(); // validate
        }
    }

    protected void pathListSelectionChanged(DialogField field) {
        List selected = foldersList.getSelectedElements();
        foldersList.enableButton(IDX_EDIT, canEdit(selected));
        foldersList.enableButton(IDX_REMOVE, canRemove(selected));
        boolean noAttributes = !hasAttributes(selected);
        foldersList.enableButton(IDX_ADD, noAttributes);
    }

    private boolean hasAttributes(List selElements) {
        if (selElements.size() == 0) {
            return false;
        }
        for (int i = 0; i < selElements.size(); i++) {
            if (selElements.get(i) instanceof PathListElementAttribute) {
                return true;
            }
        }
        return false;
    }

    private void removeEntry() {
        List selElements = foldersList.getSelectedElements();
        for (int i = selElements.size() - 1; i >= 0; i--) {
            Object elem = selElements.get(i);
            if (elem instanceof PathListElementAttribute) {
                PathListElementAttribute attrib = (PathListElementAttribute) elem;
                String key = attrib.getKey();
                Object value = null;
                // TODO null is ok?
                Object defaultValue = null;
                if (key.equals(PathListElement.EXCLUSION) || key.equals(PathListElement.INCLUSION)) {
                    value = new Path[0];
                    defaultValue = value;
                }
                attrib.getParent().setAttribute(key, value, defaultValue);
                selElements.remove(i);
            }
        }
        if (selElements.isEmpty()) {
            foldersList.refresh();
        } else {
            foldersList.removeElements(selElements);
        }
        dialogFieldChanged(destPathDialogField);
    }

    private boolean canRemove(List selElements) {
        if (selElements.size() == 0) {
            return false;
        }
        for (int i = 0; i < selElements.size(); i++) {
            Object elem = selElements.get(i);
            if (elem instanceof PathListElementAttribute) {
                PathListElementAttribute attrib = (PathListElementAttribute) elem;
                String key = attrib.getKey();
                if (PathListElement.INCLUSION.equals(key)) {
                    if (((IPath[]) attrib.getValue()).length == 0) {
                        return false;
                    }
                } else if (PathListElement.EXCLUSION.equals(key)) {
                    if (((IPath[]) attrib.getValue()).length == 0) {
                        return false;
                    }
                } else if (attrib.getValue() == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canEdit(List selElements) {
        if (selElements.size() != 1) {
            return false;
        }
        Object elem = selElements.get(0);
        if (elem instanceof PathListElement) {
            return false;
        }
        if (elem instanceof PathListElementAttribute) {
            return true;
        }
        return false;
    }

    protected void pathListDialogFieldChanged(DialogField field) {
        if (project == null) {
            // not initialized
            return;
        }

        if (field == useFolderOutputsField) {
            if (!useFolderOutputsField.isSelected()) {
                int nFolders = foldersList.getSize();
                for (int i = 0; i < nFolders; i++) {
                    PathListElement cpe = (PathListElement) foldersList.getElement(i);
                    cpe.setAttribute(PathListElement.DESTINATION, null, null);
                }
            }
            foldersList.refresh();
        } /*
         * else if (field == useVariablesField) { if (!useVariablesField.isSelected()) { int
         * nFolders = foldersList.getSize(); for (int i = 0; i < nFolders; i++) { PathListElement
         * cpe = (PathListElement) foldersList.getElement(i);
         * cpe.setAttribute(PathListElement.VARIABLES, null); } } foldersList.refresh(); }
         */else if (field == foldersList) {
             updatePatternList();
         } else if (field == enableFileSyncField) {
             boolean ok;
             boolean selected = enableFileSyncField.isSelected();
             if (selected) {
                 ok = ProjectHelper.addBuilder(project);
             } else {
                 ok = ProjectHelper.disableBuilder(project);
             }
             if (!ok) {
                 String title = "Error";
                 String message = "Changing project builder properties failed.";
                 MessageDialog.openInformation(getShell(), title, message);
             } else {
                 enableInputControls(selected);
             }
         }
    }

    protected void enableInputControls(boolean selected) {
        useFolderOutputsField.setEnabled(selected);
        // useVariablesField.setEnabled(selected);
        useCurrentDateField.setEnabled(selected);
        includeTeamFilesField.setEnabled(selected);

        delayedCopyDeleteField.setEnabled(FileSyncPlugin.getDefault().isRseAvailable() && selected);

        destPathDialogField.setEnabled(selected);
        variablesDialogField.setEnabled(selected);
        foldersList.setEnabled(selected);
        if (selected) {
            updateDefaultDestinationPathStatus();
        } else {
            destFolderStatus.setOK();
        }
        statusChanged(destFolderStatus);
    }

    @SuppressWarnings("unchecked")
    private void updatePatternList() {
        List<PathListElement> srcelements = foldersList.getElements();

        List<PathListElement> oldmappings = mappingList;
        List<PathListElement> newMappings = new ArrayList<PathListElement>(mappingList);
        for (int i = 0; i < oldmappings.size(); i++) {
            PathListElement cpe = oldmappings.get(i);
            if (!srcelements.contains(cpe)) {
                newMappings.remove(cpe);
            } else {
                // let be only really new elements in the updated list
                srcelements.remove(cpe);
            }
        }
        if (!srcelements.isEmpty()) {
            for (int i = 0; i < srcelements.size(); i++) {
                PathListElement cpe = srcelements.get(i);
                if (!newMappings.contains(cpe)) {
                    newMappings.add(cpe);
                }
            }
        }
        mappingList = newMappings;
    }

    private PathListElement openNewFolderDialog(PathListElement existing) {
        NewFolderDialog dialog = new NewFolderDialog(getShell(), project);
        dialog.setTitle("Create new folder...");
        if (dialog.open() == Window.OK) {
            IResource createdFolder = (IResource) dialog.getResult()[0];
            return newFolderElement(createdFolder);
        }
        return null;
    }

    private void askForAddingExclusionPatternsDialog(List newEntries, Set modifiedEntries) {
        fixNestingConflicts(newEntries, foldersList.getElements(), modifiedEntries);
        if (!modifiedEntries.isEmpty()) {
            String title = "Folder added";
            String message = "Exclusion filters have been added to nesting folders";
            MessageDialog.openInformation(getShell(), title, message);
        }
    }

    private PathListElement[] openFolderDialog(PathListElement existing) {

        Class<?>[] acceptedClasses = new Class<?>[] { IProject.class, IFolder.class };
        List<IContainer> existingContainers = getExistingContainers(null);

        IProject[] allProjects = workspaceRoot.getProjects();
        ArrayList<IProject> rejectedElements = new ArrayList<IProject>(allProjects.length);
        IProject currProject = project;
        for (int i = 0; i < allProjects.length; i++) {
            if (!allProjects[i].equals(currProject)) {
                rejectedElements.add(allProjects[i]);
            }
        }
        ViewerFilter filter = new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());

        ILabelProvider lp = new WorkbenchLabelProvider();
        ITreeContentProvider cp = new BaseWorkbenchContentProvider();

        String title = "Folder Selection";
        String message = "&Choose folders to be added to the synchronization mapping:";

        MultipleFolderSelectionDialog dialog = new MultipleFolderSelectionDialog(getShell(), lp, cp);
        dialog.setExisting(existingContainers.toArray());
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.addFilter(filter);
        dialog.setInput(project.getParent());
        if (existing == null) {
            dialog.setInitialFocus(project);
        } else {
            dialog.setInitialFocus(existing.getResource());
        }
        if (dialog.open() == Window.OK) {
            Object[] elements = dialog.getResult();
            PathListElement[] res = new PathListElement[elements.length];
            for (int i = 0; i < res.length; i++) {
                IResource elem = (IResource) elements[i];
                res[i] = newFolderElement(elem);
            }
            return res;
        }
        return null;
    }

    private IPath openFileDialog(IPath path) {
        Class[] acceptedClasses = new Class[] { IFile.class, IFolder.class, IProject.class };
        ISelectionStatusValidator validator = new TypedElementSelectionValidator(acceptedClasses,
                false) {

            public IStatus validate(Object[] elements) {
                if (elements.length > 1 || elements.length == 0 || !(elements[0] instanceof IFile)) {
                    return errorStatus;
                }
                return okStatus;
            }
        };

        IProject[] allProjects = workspaceRoot.getProjects();
        ArrayList<IContainer> rejectedElements = new ArrayList<IContainer>(allProjects.length);
        for (int i = 0; i < allProjects.length; i++) {
            if (!allProjects[i].equals(project)) {
                rejectedElements.add(allProjects[i]);
            }
        }
        ViewerFilter filter = new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());

        ILabelProvider lp = new WorkbenchLabelProvider();
        ITreeContentProvider cp = new WorkbenchContentProvider();

        IResource initSelection = null;
        if (path != null) {
            initSelection = project.findMember(path);
        }

        ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(getShell(), lp, cp);
        dialog.setTitle("Variables");
        dialog.setValidator(validator);
        dialog.setMessage("Select file with variables definition");
        dialog.addFilter(filter);
        dialog.setInput(workspaceRoot);
        dialog.setInitialSelection(initSelection);
        dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));

        if (dialog.open() == Window.OK) {
            return ((IFile) dialog.getFirstResult()).getProjectRelativePath();
        }
        return null;
    }

    private List<IContainer> getExistingContainers(PathListElement existing) {
        List<IContainer> res = new ArrayList<IContainer>();
        List<IContainer> cplist = foldersList.getElements();
        for (int i = 0; i < cplist.size(); i++) {
            PathListElement elem = (PathListElement) cplist.get(i);
            if (elem != existing) {
                IResource resource = elem.getResource();
                if (resource instanceof IContainer) { // defensive code
                    res.add((IContainer) resource);
                }
            }
        }
        return res;
    }

    private PathListElement newFolderElement(IResource res) {
        Assert.isNotNull(res);
        return new PathListElement(project, res.getFullPath(), res, defPathCallback,
                defVariablesCallback);
    }

    /*
     * @see BuildPathBasePage#getSelection
     */
    public List getSelection() {
        return foldersList.getSelectedElements();
    }

    /*
     * @see BuildPathBasePage#setSelection
     */
    public void setSelection(List selElements) {
        foldersList.selectElements(new StructuredSelection(selElements));
    }

    protected void filterAndSetSelection(List list) {
        ArrayList res = new ArrayList(list.size());
        for (int i = list.size() - 1; i >= 0; i--) {
            Object curr = list.get(i);
            if (curr instanceof PathListElement) {
                res.add(curr);
            }
        }
        setSelection(res);
    }

    protected void fixNestingConflicts(List newEntries, List existing, Set modifiedSourceEntries) {
        for (int i = 0; i < newEntries.size(); i++) {
            PathListElement curr = (PathListElement) newEntries.get(i);
            addExclusionPatterns(curr, existing, modifiedSourceEntries);
            existing.add(curr);
        }
    }

    private void addExclusionPatterns(PathListElement newEntry, List existing, Set modifiedEntries) {
        IPath entryPath = newEntry.getPath();
        for (int i = 0; i < existing.size(); i++) {
            PathListElement curr = (PathListElement) existing.get(i);

            IPath currPath = curr.getPath();
            if (!currPath.equals(entryPath)) {
                if (currPath.isPrefixOf(entryPath)) {
                    IPath[] exclusionFilters = (IPath[]) curr
                    .getAttribute(PathListElement.EXCLUSION);
                    if (!isExcludedPath(entryPath, exclusionFilters)) {
                        IPath pathToExclude = entryPath
                        .removeFirstSegments(currPath.segmentCount())
                        .addTrailingSeparator();
                        IPath[] newExclusionFilters = new IPath[exclusionFilters.length + 1];
                        System.arraycopy(exclusionFilters, 0, newExclusionFilters, 0,
                                exclusionFilters.length);
                        newExclusionFilters[exclusionFilters.length] = pathToExclude;
                        curr.setAttribute(PathListElement.EXCLUSION, newExclusionFilters, null);
                        modifiedEntries.add(curr);
                    }
                } else if (entryPath.isPrefixOf(currPath)) {
                    IPath[] exclusionFilters = (IPath[]) newEntry
                    .getAttribute(PathListElement.EXCLUSION);

                    if (!isExcludedPath(currPath, exclusionFilters)) {
                        IPath pathToExclude = currPath
                        .removeFirstSegments(entryPath.segmentCount())
                        .addTrailingSeparator();
                        IPath[] newExclusionFilters = new IPath[exclusionFilters.length + 1];
                        System.arraycopy(exclusionFilters, 0, newExclusionFilters, 0,
                                exclusionFilters.length);
                        newExclusionFilters[exclusionFilters.length] = pathToExclude;
                        newEntry.setAttribute(PathListElement.EXCLUSION, newExclusionFilters, null);
                        modifiedEntries.add(newEntry);
                    }
                }
            }

        }
    }

    /**
     * Copy from StatusUtil Applies the status to the status line of a dialog page.
     */
    public static void applyToStatusLine(DialogPage page, IStatus status) {
        String message = status.getMessage();
        switch (status.getSeverity()) {
        case IStatus.OK:
            page.setMessage(message, IMessageProvider.NONE);
            page.setErrorMessage(null);
            break;
        case IStatus.WARNING:
            page.setMessage(message, IMessageProvider.WARNING);
            page.setErrorMessage(null);
            break;
        case IStatus.INFO:
            page.setMessage(message, IMessageProvider.INFORMATION);
            page.setErrorMessage(null);
            break;
        default:
            if (message.length() == 0) {
                message = null;
            }
            page.setMessage(null);
            page.setErrorMessage(message);
            break;
        }
    }

    /**
     * copy from JavaModelUtils
     *
     * @param resourcePath
     * @param exclusionPatterns
     */
    public static boolean isExcludedPath(IPath resourcePath, IPath[] exclusionPatterns) {
        char[] path = resourcePath.toString().toCharArray();
        for (int i = 0, length = exclusionPatterns.length; i < length; i++) {
            char[] pattern = exclusionPatterns[i].toString().toCharArray();
            if (CharOperation.pathMatch(pattern, path, true, '/')) {
                return true;
            }
        }
        return false;
    }

    protected void performDefaults() {
        IEclipsePreferences preferences = getPreferences(false);
        String defPath = preferences.get(ProjectProperties.KEY_DEFAULT_DESTINATION, "");
        IPath projectPath = project.getLocation();

        IPath path = pathVariableHelper.resolveVariable(defPath, projectPath);
        if (path == null) {
            destPathDialogField.setText("");
        } else {
            destPathDialogField.setText(path.toOSString());
        }
        IPath variables = readVariablesPath(preferences);
        if (path == null) {
            variablesDialogField.setText("");
        } else {
            variablesDialogField.setText(variables.toPortableString());
        }
    }

    public boolean performOk() {
        if (!destFolderStatus.isOK()) {
            return false;
        }
        if (!hasChangesInDialog()) {
            return true;
        }

        // store the value in the owner text field
        IEclipsePreferences preferences = getPreferences(false);
        ProjectProperties properties = ProjectProperties.getInstance(project);

        properties.setIgnorePreferenceListeners(true);

        try {
            preferences.clear();
        } catch (BackingStoreException e) {
            FileSyncPlugin.log("Cannot clear preferences for project '" + project.getName() + "'",
                    e, IStatus.ERROR);
        } catch (IllegalStateException e) {
            FileSyncPlugin.log("FileSync project preferences (for project '" + project.getName()
                    + "') error: " + e.getMessage(), e, IStatus.ERROR);
            return false;
        }

        for (int i = 0; i < mappingList.size(); i++) {
            preferences.put(FileMapping.FULL_MAP_PREFIX + i, (mappingList.get(i))
                    .getMapping().encode());
        }

        IPath projectPath = project.getLocation();
        String defPath = pathVariableHelper.unResolveVariable(getDefaultDestinationPath(),
                projectPath);
        if (defPath == null) {
            defPath = "";
        }
        preferences.put(ProjectProperties.KEY_DEFAULT_DESTINATION, defPath);

        IPath defVars = getDefaultVariablesPath();
        if (defVars == null) {
            preferences.put(ProjectProperties.KEY_DEFAULT_VARIABLES, "");
        } else {
            preferences.put(ProjectProperties.KEY_DEFAULT_VARIABLES, defVars.toPortableString());
        }

        preferences.put(ProjectProperties.KEY_USE_CURRENT_DATE, ""
                + useCurrentDateField.isSelected());
        preferences.put(ProjectProperties.KEY_INCLUDE_TEAM_PRIVATE, ""
                + includeTeamFilesField.isSelected());
        preferences.put(ProjectProperties.KEY_DELAYED_COPY_DELETE, ""
                + delayedCopyDeleteField.isSelected());

        if (preferences.get("WARNING", null) == null) {
            preferences.put("WARNING", "DO NOT MODIFY THIS FILE IF YOU DON'T UNDERSTAND");
        }
        properties.setIgnorePreferenceListeners(false);
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            FileSyncPlugin.log("Cannot store preferences for project '" + project.getName() + "'",
                    e, IStatus.ERROR);
        }
        return true;
    }

    /**
     * @return Returns the preferences.
     */
    protected IEclipsePreferences getPreferences(boolean forceSync) {
        ProjectProperties properties = ProjectProperties.getInstance(project);
        boolean wasDisabled = true;
        if (forceSync) {
            List listeners = properties.getProjectPreferenceChangeListeners();
            for (int i = 0; i < listeners.size(); i++) {
                FileSyncBuilder b = (FileSyncBuilder) listeners.get(i);
                wasDisabled = b.isDisabled();
                if (!b.isDisabled()) {
                    b.setDisabled(true);
                }
            }
        }
        IEclipsePreferences preferences = properties.getPreferences(forceSync);
        if (forceSync) {
            List listeners = properties.getProjectPreferenceChangeListeners();
            for (int i = 0; i < listeners.size(); i++) {
                FileSyncBuilder b = (FileSyncBuilder) listeners.get(i);
                if (!wasDisabled) {
                    b.setDisabled(false);
                }
            }
        }
        return preferences;
    }

    /*
     * (non-Javadoc)
     *
     * @see IStatusChangeListener#statusChanged
     */
    public void statusChanged(IStatus status) {
        setValid(!status.matches(IStatus.ERROR));
        applyToStatusLine(this, status);
    }

    protected void init(IPath outputLocation, IPath variables, FileMapping[] mappings) {
        List newClassPath = new ArrayList();
        for (int i = 0; i < mappings.length; i++) {
            newClassPath.add(new PathListElement(project, mappings[i], defPathCallback,
                    defVariablesCallback));
        }

        Collections.sort(newClassPath, pathComparator);

        List exportedEntries = new ArrayList();
        for (int i = 0; i < newClassPath.size(); i++) {
            PathListElement curr = (PathListElement) newClassPath.get(i);
            exportedEntries.add(curr);
        }
        mappingList = newClassPath;
        defDestinationPath = outputLocation;
        // inits the dialog field
        if (outputLocation != null) {
            if (FileSyncPlugin.getDefault().getFsPathUtil().isUriIncluded(outputLocation)) {
                destPathDialogField.setText(FileSyncPlugin.getDefault().getFsPathUtil().getUri(
                        outputLocation).toString());
            } else {
                destPathDialogField.setText(outputLocation.toOSString());
            }
        }
        destPathDialogField.enableButton(true);
        if (variables != null) {
            variablesDialogField.setText(variables.toPortableString());
        }
        variablesDialogField.enableButton(true);
        oldMappings = getEncodedSettings();
    }

    protected void updateUI() {
        destPathDialogField.refresh();
        variablesDialogField.refresh();
        init();
        doStatusLineUpdate();
    }

    private String getEncodedSettings() {
        StringBuffer buf = new StringBuffer();
        // the host or scheme can be different even if the path is the same
        String fqString = FileSyncPlugin.getDefault().getFsPathUtil()
        .toFqString(defDestinationPath);
        PathListElement.appendEncodeString(fqString, buf).append(';');
        PathListElement.appendEncodePath(getDefaultVariablesPath(), buf).append(';');

        int nElements = mappingList.size();
        buf.append('[').append(nElements).append(']');
        for (int i = 0; i < nElements; i++) {
            PathListElement elem = mappingList.get(i);
            elem.appendEncodedSettings(buf);
        }
        return buf.toString();
    }

    public boolean hasChangesInDialog() {
        String currSettings = getEncodedSettings();
        boolean b = !currSettings.equals(oldMappings);

        if (b) {
            return true;
        }

        IEclipsePreferences preferences = getPreferences(false);
        boolean useCurrentDate = preferences.getBoolean(ProjectProperties.KEY_USE_CURRENT_DATE,
                false);
        boolean useCurrentDateNew = useCurrentDateField.isSelected();

        if (useCurrentDateNew != useCurrentDate) {
            return true;
        }

        boolean includeTeamFiles = preferences.getBoolean(
                ProjectProperties.KEY_INCLUDE_TEAM_PRIVATE, false);

        boolean includeTeamFilesNew = includeTeamFilesField.isSelected();
        if (includeTeamFiles != includeTeamFilesNew) {
            return true;
        }

        boolean delayedCopyDelete = preferences.getBoolean(
                ProjectProperties.KEY_DELAYED_COPY_DELETE, FileSyncPlugin.getDefault()
                .isDefaultDelayedCopy());

        boolean delayedCopyDeleteNew = delayedCopyDeleteField.isSelected();
        if (delayedCopyDelete != delayedCopyDeleteNew) {
            return true;
        }

        return false;
    }

    /**
     * @return Returns the Java project. Can return <code>null<code> if the page has not
     * been initialized.
     */
    public IProject getProject() {
        return project;
    }

    /**
     * @return Returns the current output location. Note that the path returned must not be valid.
     */
    public IPath getDefaultDestinationPath() {
        String text = destPathDialogField.getText();
        if ((text == null || text.trim().length() == 0)) {
            return null;
        }
        return FileSyncPlugin.getDefault().getFsPathUtil().create(text).makeAbsolute();
    }

    /**
     * @return Returns the current class path (raw). Note that the entries returned must not be
     *         valid.
     */
    public FileMapping[] getFileMappings() {
        int nElements = mappingList.size();
        FileMapping[] entries = new FileMapping[mappingList.size()];

        for (int i = 0; i < nElements; i++) {
            PathListElement currElement = mappingList.get(i);
            entries[i] = currElement.getMapping();
        }
        return entries;
    }

    void changeControlPressed(DialogField field) {
        if (field == destPathDialogField) {
            DirectoryDialog dialog = new DirectoryDialog(getShell());
            dialog.setMessage("Select default target folder");
            if (destPathDialogField.getText() != null) {
                dialog.setFilterPath(destPathDialogField.getText());
            }
            String absPath = dialog.open();
            if (absPath == null) {
                return;
            }
            IPath destPath = new Path(absPath);
            destPathDialogField.setText(destPath.toOSString());
        } else if (field == variablesDialogField) {
            String curr = variablesDialogField.getText();
            IPath currPath = null;
            if (curr != null && curr.length() > 0) {
                currPath = new Path(curr);
            }
            IPath destPath = openFileDialog(currPath);

            if (destPath == null) {
                variablesDialogField.setText("");
            } else {
                variablesDialogField.setText(destPath.toPortableString());
            }
            foldersList.refresh();
        }
    }

    void dialogFieldChanged(DialogField field) {
        if (field == destPathDialogField) {
            updateDefaultDestinationPathStatus();
            doStatusLineUpdate();
        } else if (field == variablesDialogField) {
            if (foldersList != null) {
                foldersList.refresh();
            }
        }
    }

    private void doStatusLineUpdate() {
        if (Display.getCurrent() != null) {
            statusChanged(destFolderStatus);
        }
    }

    /**
     * Validates output location & build path.
     */
    private void updateDefaultDestinationPathStatus() {
        defDestinationPath = null;

        if (!usesDefaultOutputFolder()) {
            destFolderStatus.setOK();
            return;
        }

        String text = destPathDialogField.getText();
        if ((text == null || text.trim().length() == 0)) {
            destFolderStatus.setError("Please specify default target folder!");
            return;
        }

        try {
            defDestinationPath = getDefaultDestinationPath();
        } catch (FileSyncException e) {
            destFolderStatus.setError("Default target URI is invalid.");
            return;
        }

        File f = new File(defDestinationPath.toOSString());
        // if exists, must be a folder with read/write rights
        if (f.exists() && (f.isFile() || !f.canRead() || !f.canWrite())) {
            destFolderStatus.setError("Default target folder is invalid: " + f);
            return;
        }
        destFolderStatus.setOK();
    }


    class PathContainerAdapter implements ITreeListAdapter, IDialogFieldListener {

        private final Object[] EMPTY_ARR = new Object[0];

        public void customButtonPressed(TreeListDialogField field, int index) {
            pathListButtonPressed(field, index);
        }

        public void selectionChanged(TreeListDialogField field) {
            pathListSelectionChanged(field);
        }

        public void doubleClicked(TreeListDialogField field) {
            pathListDoubleClicked(field);
        }

        public void keyPressed(TreeListDialogField field, KeyEvent event) {
            pathListKeyPressed(field, event);
        }

        public Object[] getChildren(TreeListDialogField field, Object element) {
            if (element instanceof PathListElement) {
                return ((PathListElement) element).getChildren(!useFolderOutputsField.isSelected(),
                        false /* !useVariablesField.isSelected() */);
            }
            return EMPTY_ARR;
        }

        public Object getParent(TreeListDialogField field, Object element) {
            if (element instanceof PathListElementAttribute) {
                return ((PathListElementAttribute) element).getParent();
            }
            return null;
        }

        public boolean hasChildren(TreeListDialogField field, Object element) {
            return (element instanceof PathListElement);
        }

        public void dialogFieldChanged(DialogField field) {
            pathListDialogFieldChanged(field);
        }
    }

    class BuildPathAdapter implements IStringButtonAdapter, IDialogFieldListener {

        public void changeControlPressed(DialogField field) {
            ProjectSyncPropertyPage.this.changeControlPressed(field);
        }

        public void dialogFieldChanged(DialogField field) {
            ProjectSyncPropertyPage.this.dialogFieldChanged(field);
        }
    }
}
