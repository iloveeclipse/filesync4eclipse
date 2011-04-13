/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.actions.ActionDelegate;
import org.eclipse.ui.handlers.HandlerUtil;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.builder.FileSyncBuilder;
import de.loskutov.fs.preferences.FileSyncConstants;
import de.loskutov.fs.properties.ProjectHelper;

/**
 * @author Andrei
 */
public class ForceFileSyncActionDelegate extends ActionDelegate implements IHandler {

    private IProject project;

    public ForceFileSyncActionDelegate() {
        super();
    }

    @Override
    public void dispose() {
        project = null;
        super.dispose();
    }

    @Override
    public void run(IAction action) {
        FileSyncBuilder builder = getOrCreateBuilder();
        if(builder != null){
            sync(builder);
        }
    }

    /**
     * Public method to be able to test it
     */
    public FileSyncBuilder getOrCreateBuilder() {
        if (project == null) {
            FileSyncPlugin.error("Could not run FileSync builder - project is null!",
                    null);
            return null;
        }
        IPreferenceStore store = FileSyncPlugin.getDefault().getPreferenceStore();
        boolean askUser = store.getBoolean(FileSyncConstants.KEY_ASK_USER);
        if (ProjectHelper.isBuilderDisabled(project)) {
            if (askUser) {
                MessageDialog.openInformation(FileSyncPlugin.getShell(),
                        "FileSync builder is disabled!",
                        "Please activate FileSync builder for project '"
                                + project.getName() + "' under\n"
                                        + "Project->Properties->Builders!");
            }
            return null;
        }

        if (!ProjectHelper.hasBuilder(project)) {
            boolean ok = true; // TODO should be taken from prefs
            if(askUser){
                ok = MessageDialog.openQuestion(FileSyncPlugin.getShell(),
                        "FileSync builder is not enabled!",
                        "Should FileSync builder be enabled for project '"
                                + project.getName() + "' ?");
            }
            if (ok) {
                ProjectHelper.addBuilder(project);
            }
        }

        return new FileSyncBuilder(project);
    }

    private void sync(FileSyncBuilder builder) {
        final FileSyncBuilder finalBuilder = builder;

        final Job myJob = new Job("Full project sync") {
            @Override
            public IStatus run(IProgressMonitor monitor) {
                finalBuilder.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
                return Status.OK_STATUS;//new JobStatus(IStatus.INFO, 0, this, "", null);
            }
        };
        myJob.schedule();
    }

    //    private FileSyncBuilder getBuilder() {
    //        //        BuildManager manager = ((Workspace) ResourcesPlugin.getWorkspace()).getBuildManager();
    //        ICommand[] commands = null;
    //
    //        // TODO does not work as expected...
    //
    //        //        try {
    //        //            commands = project.getDescription().getBuildSpec();
    //        //        } catch (CoreException e) {
    //        //            FileSyncPlugin.error("Could not get builder info from project " + project.getName(), e);
    //        //            return null;
    //        //        }
    //        // the hack for problem above
    //        commands = ((Project) project).internalGetDescription().getBuildSpec(false);
    //
    //        for (int i = 0; i < commands.length; i++) {
    //            String builderName = commands[i].getBuilderName();
    //            if (FileSyncBuilder.BUILDER_ID.equals(builderName)) {
    //                return (FileSyncBuilder) ((BuildCommand) commands[i]).getBuilder();
    //            }
    //        }
    //        return null;
    //        //        ProjectInfo info = (ProjectInfo) ((Workspace) ResourcesPlugin
    //        //                .getWorkspace()).getResourceInfo(project.getFullPath(), false,
    //        //                false);
    //        //        Hashtable builders = info.getBuilders();
    //        //        final FileSyncBuilder builder = (FileSyncBuilder) builders
    //        //                .get(FileSyncBuilder.BUILDER_ID);
    //        //        return builder;
    //    }

    @Override
    public void runWithEvent(IAction action, Event event) {
        run(action);
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        if (!(selection instanceof IStructuredSelection)) {
            project = null;
            return;
        }
        IStructuredSelection ssel = (IStructuredSelection) selection;
        Object firstElement = ssel.getFirstElement();
        if(firstElement == null) {
            project = null;
            return;
        }
        project = getProject(firstElement);
        if (project != null) {
            return;
        }
        if (firstElement instanceof IResource) {
            project = ((IResource) firstElement).getProject();
        }
        if (project != null) {
            return;
        }
        if (firstElement instanceof IProjectNature) {
            project = ((IProjectNature) firstElement).getProject();
        }
    }

    public static IProject getProject(Object o) {
        if (o instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) o;
            IResource adapter = (IProject) adaptable.getAdapter(IProject.class);
            if (adapter != null) {
                return adapter.getProject();
            }
            adapter = (IResource) adaptable.getAdapter(IResource.class);
            if (adapter != null) {
                return adapter.getProject();
            }
            adapter = (IResource) adaptable.getAdapter(IFile.class);
            if (adapter != null) {
                return adapter.getProject();
            }
        }
        Object adapter = Platform.getAdapterManager().getAdapter(o, IResource.class);
        return adapter != null? ((IResource) adapter).getProject() : null;
    }


    public void addHandlerListener(IHandlerListener handlerListener) {
        // noop
    }

    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        selectionChanged(null, selection);
        run(null);
        return null;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean isHandled() {
        return true;
    }

    public void removeHandlerListener(IHandlerListener handlerListener) {
        // noop
    }

}
