/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.builder.FileSyncBuilder;

/**
 * @author Andrei
 */
public class ProjectHelper {
    /**
     * Will be run after workbench is started and w.window is opened
     */
    public ProjectHelper() {
        super();
    }

    public static boolean hasBuilder(IProject project){
        if(!project.isAccessible()){
            return false;
        }
        IProjectDescription desc;
        try {
            desc = project.getDescription();
        } catch (CoreException e) {
            FileSyncPlugin.log("hasBuilder(): failed for project '"
                + project.getName() + "'", e, IStatus.INFO);
            return false;
        }
        ICommand[] commands = desc.getBuildSpec();
        boolean found = false;

        for (int i = 0; i < commands.length; i++) {
            String builderName = commands[i].getBuilderName();
            if (FileSyncBuilder.BUILDER_ID.equals(builderName)) {
                found = true;
                break;
            }
        }
        return found;
    }

    public static boolean isBuilderDisabled(IProject project){
        if(project == null || !project.isAccessible()){
            return false;
        }
        IProjectDescription desc;
        try {
            desc = project.getDescription();
        } catch (CoreException e) {
            FileSyncPlugin.log("addBuilder(): failed for project '"
                + project.getName() + "'", e, IStatus.WARNING);
            return false;
        }
        return isBuilderDisabled(project, desc);
    }

    private static boolean isBuilderDisabled(IProject project, IProjectDescription desc){
        ICommand[] commands = desc.getBuildSpec();
        boolean disabled = false;

        for (int i = 0; i < commands.length; i++) {
            String builderName = commands[i].getBuilderName();
            if (FileSyncBuilder.BUILDER_ID.equals(builderName)) {
                disabled = false;
                break;
            }
            // see ExternalToolBuilder.ID
            if(isBuilderDeactivated(commands[i])) {
                disabled = true;
                break;
            }
        }
        return disabled;
    }

    private static boolean isBuilderDeactivated(ICommand command){
        // see ExternalToolBuilder.ID
        if(command.getBuilderName().equals("org.eclipse.ui.externaltools.ExternalToolBuilder")) {
            /*
             * check for deactivated builder
             */
            Map arguments = command.getArguments();
            String externalLaunch = (String) arguments
                    .get("LaunchConfigHandle"); // see BuilderUtils.LAUNCH_CONFIG_HANDLE);
            if(externalLaunch != null
                    && externalLaunch.indexOf(FileSyncBuilder.BUILDER_ID) >=0){
                return true;
            }
        }
        return false;
    }

    public static boolean addBuilder(IProject project) {
        if(!project.isAccessible()){
            return false;
        }
        if(hasBuilder(project)){
            return true;
        }

        IProjectDescription desc;
        try {
            desc = project.getDescription();
        } catch (CoreException e) {
            FileSyncPlugin.log("addBuilder(): failed for project '"
                + project.getName() + "'", e, IStatus.WARNING);
            return false;
        }

        if(isBuilderDisabled(project, desc)){
            removeDisabledBuilder(desc);
        }

        ICommand command = desc.newCommand();
        command.setBuilderName(FileSyncBuilder.BUILDER_ID);

        ICommand[] commands = desc.getBuildSpec();
        ICommand[] newCommands = new ICommand[commands.length + 1];

        // Add it after other builders.
        System.arraycopy(commands, 0, newCommands, 0, commands.length);
        newCommands[newCommands.length-1] = command;
        desc.setBuildSpec(newCommands);
        try {
            project.setDescription(desc, IResource.FORCE
                    | IResource.KEEP_HISTORY, null);

        } catch (CoreException e) {
            FileSyncPlugin.log(
                    "addBuilder(): failed to change .project file for project '"
                    + project.getName() + "'", e, IStatus.WARNING);
            return false;
        }
        return true;
    }

    private static void removeDisabledBuilder(IProjectDescription desc) {
        ICommand[] commands = desc.getBuildSpec();

        List list = new ArrayList(commands.length);

        for (int i = 0; i < commands.length; i++) {
            if (!isBuilderDeactivated(commands[i])) {
                list.add(commands[i]);
            }
        }

        ICommand[] newCommands = (ICommand[]) list.toArray(new ICommand[list.size()]);

        desc.setBuildSpec(newCommands);
    }

    public static boolean disableBuilder(IProject project) {
        if(!project.isAccessible()){
            return false;
        }

        IProjectDescription desc;
        try {
            desc = project.getDescription();
        } catch (CoreException e) {
            FileSyncPlugin.log("hasBuilder(): failed for project '"
                + project.getName() + "'", e, IStatus.INFO);
            return false;
        }
        if(isBuilderDisabled(project, desc)){
            removeDisabledBuilder(desc);
        }

        ICommand[] commands = desc.getBuildSpec();

        List list = new ArrayList(commands.length);

        for (int i = 0; i < commands.length; i++) {
            String builderName = commands[i].getBuilderName();
            if (!FileSyncBuilder.BUILDER_ID.equals(builderName)) {
                list.add(commands[i]);
            }
        }

        ICommand[] newCommands = (ICommand[]) list.toArray(new ICommand[list.size()]);

        desc.setBuildSpec(newCommands);
        try {
            project.setDescription(desc, IResource.FORCE
                    | IResource.KEEP_HISTORY, null);
        } catch (CoreException e) {
            FileSyncPlugin.log(
                    "addBuilder(): failed to change .project file for project '"
                    + project.getName() + "'", e, IStatus.WARNING);
            return false;
        }

        ProjectProperties properties = ProjectProperties.getInstance(project);
        if(properties != null){
            List listeners = properties.getProjectPreferenceChangeListeners();
            for (int i = 0; i < listeners.size(); i++) {
                FileSyncBuilder b = (FileSyncBuilder) listeners.get(i);
                b.setDisabled(true);
            }
        }
        ProjectProperties.removeInstance(project);
        return true;

    }
}