/*******************************************************************************
 * Copyright (c) 2009 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.dialogs.DialogField;
import de.loskutov.fs.dialogs.IDialogFieldListener;
import de.loskutov.fs.dialogs.IStringButtonAdapter;
import de.loskutov.fs.dialogs.LayoutUtil;
import de.loskutov.fs.dialogs.StringButtonDialogField;
import de.loskutov.fs.dialogs.TypedElementSelectionValidator;
import de.loskutov.fs.dialogs.TypedViewerFilter;

public class InclusionExclusionEntryDialog extends
org.eclipse.jface.dialogs.StatusDialog {

    private final StringButtonDialogField exclPatternDialog;

    private Status exclPatternStatus;

    private IContainer currSourceFolder;

    private String exclPattern;

    private final List existingPatterns;

    private final boolean isExclusion;

    public InclusionExclusionEntryDialog(Shell parent, boolean isExclusion,
            String patternToEdit, List existingPatterns,
            PathListElement entryToEdit) {
        super(parent);
        this.isExclusion = isExclusion;
        this.existingPatterns = existingPatterns;
        String title, message;
        if (isExclusion) {
            if (patternToEdit == null) {
                title = "Add Exclusion Pattern";
            } else {
                title = "Edit Exclusion Pattern";
            }
            message = MessageFormat.format(
                    "E&xclusion pattern (Path relative to ''{0}''):",
                    new Object[] { entryToEdit.getPath().makeRelative()
                            .toString() });
        } else {
            if (patternToEdit == null) {
                title = "Add Inclusion Pattern";
            } else {
                title = "Edit Inclusion Pattern";
            }
            message = MessageFormat.format(
                    "I&nclusion pattern (Path relative to ''{0}''):",
                    new Object[] { entryToEdit.getPath().makeRelative()
                            .toString() });
        }
        setTitle(title);
        if (patternToEdit != null) {
            existingPatterns.remove(patternToEdit);
        }

        IWorkspaceRoot root = entryToEdit.getProject().getWorkspace().getRoot();
        IResource res = root.findMember(entryToEdit.getPath());
        if (res instanceof IContainer) {
            currSourceFolder = (IContainer) res;
        }

        exclPatternStatus = new Status(IStatus.OK, FileSyncPlugin.PLUGIN_ID,
                IStatus.OK, "", null);

        ExclusionPatternAdapter adapter = new ExclusionPatternAdapter();
        exclPatternDialog = new StringButtonDialogField(adapter);
        exclPatternDialog.setLabelText(message);
        exclPatternDialog.setButtonLabel("Bro&wse...");
        exclPatternDialog.setDialogFieldListener(adapter);
        exclPatternDialog.enableButton(currSourceFolder != null);

        if (patternToEdit == null) {
            exclPatternDialog.setText("");
        } else {
            exclPatternDialog.setText(patternToEdit);
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite composite = (Composite) super.createDialogArea(parent);

        int widthHint = convertWidthInCharsToPixels(60);

        Composite inner = new Composite(composite, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        layout.numColumns = 2;
        inner.setLayout(layout);

        Label description = new Label(inner, SWT.WRAP);

        if (isExclusion) {
            description
            .setText("Enter a pattern for excluding files from the "
                    + "source folder. Allowed wildcards are '*', '?' and '**'. "
                    + "Examples: 'java/util/A*.java', 'java/util/', '**/Test*'.");
        } else {
            description
            .setText("Enter a pattern for including files to the "
                    + "source folder. Allowed wildcards are '*', '?' and '**'. "
                    + "Examples: 'java/util/A*.java', 'java/util/', '**/Test*'.");
        }
        GridData gd = new GridData();
        gd.horizontalSpan = 2;
        gd.widthHint = convertWidthInCharsToPixels(80);
        description.setLayoutData(gd);

        exclPatternDialog.doFillIntoGrid(inner, 3);

        LayoutUtil.setWidthHint(exclPatternDialog.getLabelControl(null),
                widthHint);
        LayoutUtil
        .setHorizontalSpan(exclPatternDialog.getLabelControl(null), 2);

        LayoutUtil.setWidthHint(exclPatternDialog.getTextControl(null),
                widthHint);
        LayoutUtil
        .setHorizontalGrabbing(exclPatternDialog.getTextControl(null));

        exclPatternDialog.postSetFocusOnDialogField(parent.getDisplay());
        applyDialogFont(composite);
        return composite;
    }

    class ExclusionPatternAdapter implements IDialogFieldListener,
    IStringButtonAdapter {

        @Override
        public void dialogFieldChanged(DialogField field) {
            doStatusLineUpdate();
        }

        @Override
        public void changeControlPressed(DialogField field) {
            doChangeControlPressed();
        }
    }

    protected void doChangeControlPressed() {
        IPath pattern = chooseExclusionPattern();
        if (pattern != null) {
            exclPatternDialog.setText(pattern.toString());
        }
    }

    protected void doStatusLineUpdate() {
        checkIfPatternValid();
        updateStatus(exclPatternStatus);
    }

    protected void checkIfPatternValid() {
        String pattern = exclPatternDialog.getText().trim();
        if (pattern.length() == 0) {
            exclPatternStatus = new Status(IStatus.ERROR,
                    FileSyncPlugin.PLUGIN_ID, IStatus.OK, "Enter a pattern.",
                    null);
            return;
        }
        IPath path = new Path(pattern);
        if (path.isAbsolute() || path.getDevice() != null) {
            exclPatternStatus = new Status(IStatus.ERROR,
                    FileSyncPlugin.PLUGIN_ID, IStatus.OK,
                    "Pattern must be a relative path.", null);
            return;
        }
        if (existingPatterns.contains(pattern)) {
            exclPatternStatus = new Status(IStatus.ERROR,
                    FileSyncPlugin.PLUGIN_ID, IStatus.OK,
                    "Pattern already exists.", null);
            return;
        }

        exclPattern = pattern;
        exclPatternStatus = new Status(IStatus.OK, FileSyncPlugin.PLUGIN_ID,
                IStatus.OK, "", null);
    }

    public String getExclusionPattern() {
        return exclPattern;
    }

    private IPath chooseExclusionPattern() {
        Class[] acceptedClasses = new Class[] { IFolder.class, IFile.class };
        ISelectionStatusValidator validator = new TypedElementSelectionValidator(
                acceptedClasses, false);
        ViewerFilter filter = new TypedViewerFilter(acceptedClasses);

        ILabelProvider lp = new WorkbenchLabelProvider();
        ITreeContentProvider cp = new WorkbenchContentProvider();

        IPath initialPath = new Path(exclPatternDialog.getText());
        IResource initialElement = null;
        IContainer curr = currSourceFolder;
        int nSegments = initialPath.segmentCount();
        for (int i = 0; i < nSegments; i++) {
            IResource elem = curr.findMember(initialPath.segment(i));
            if (elem != null) {
                initialElement = elem;
            }
            if (elem instanceof IContainer) {
                curr = (IContainer) elem;
            } else {
                break;
            }
        }
        String title, message;
        if (isExclusion) {
            title = "Exclusion Pattern Selection";
            message = "&Choose a folder or file to exclude:";
        } else {
            title = "Inclusion Pattern Selection";
            message = "&Choose a folder or file to include:";
        }

        ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(
                getShell(), lp, cp);
        dialog.setTitle(title);
        dialog.setValidator(validator);
        dialog.setMessage(message);
        dialog.addFilter(filter);
        dialog.setInput(currSourceFolder);
        dialog.setInitialSelection(initialElement);
        dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));

        if (dialog.open() == Window.OK) {
            IResource res = (IResource) dialog.getFirstResult();
            IPath path = res.getFullPath().removeFirstSegments(
                    currSourceFolder.getFullPath().segmentCount())
                    .makeRelative();
            if (res instanceof IContainer) {
                return path.addTrailingSeparator();
            }
            return path;
        }
        return null;
    }
}
