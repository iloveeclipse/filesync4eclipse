/*******************************************************************************
 * Copyright (c) 2009 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.fs.properties;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import de.loskutov.fs.FileSyncPlugin;

/**
 * @author Andrey
 */
public class SupportPanel {
    static void createSupportLinks(Composite defPanel) {
        Group commonPanel = new Group(defPanel, SWT.NONE);
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, false);
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        layout.marginWidth = 0;
        commonPanel.setLayout(layout);
        commonPanel.setLayoutData(gridData);
        commonPanel.setText("Support FileSync plugin and get support too :-)");

        Label label = new Label(commonPanel, SWT.NONE);
        label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        label.setText("Feel free to support FileSync plugin in the way you like:");

        Font font = JFaceResources.getFontRegistry().getBold(
                JFaceResources.getDialogFont().getFontData()[0].getName());

        Link link = new Link(commonPanel, SWT.NONE);
        link.setFont(font);
        link.setText(" - <a>visit homepage</a>");
        link.addListener (SWT.Selection, new Listener () {
            @Override
            public void handleEvent(Event event) {
                handleUrlClick("https://github.com/iloveeclipse/plugins/wiki/FileSync");
            }
        });

        link = new Link(commonPanel, SWT.NONE);
        link.setFont(font);
        link.setText(" - <a>report issue or feature request</a>");
        link.addListener (SWT.Selection, new Listener () {
            @Override
            public void handleEvent(Event event) {
                handleUrlClick("https://github.com/iloveeclipse/filesync4eclipse/issues");
            }
        });

        link = new Link(commonPanel, SWT.NONE);
        link.setFont(font);
        link.setText(" - <a>add to your favorites at Eclipse MarketPlace</a>");
        link.setToolTipText("You need a valid bugzilla account at Eclipse.org!");
        link.addListener (SWT.Selection, new Listener () {
            @Override
            public void handleEvent(Event event) {
                handleUrlClick("http://marketplace.eclipse.org/content/filesync");
            }
        });

        link = new Link(commonPanel, SWT.NONE);
        link.setFont(font);
        link.setText(" - <a>make a donation to support plugin development</a>");
        link.setToolTipText("You do NOT need a PayPal account!");
        link.addListener (SWT.Selection, new Listener () {
            @Override
            public void handleEvent(Event event) {
                handleUrlClick("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=R5SHJLNGUXKHU");
            }
        });
    }

    private static void handleUrlClick(final String urlStr) {
        try {
            IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
            IWebBrowser externalBrowser = support.getExternalBrowser();
            if(externalBrowser != null){
                externalBrowser.openURL(new URL(urlStr));
            } else {
                IWebBrowser browser = support.createBrowser(urlStr);
                if(browser != null){
                    browser.openURL(new URL(urlStr));
                }
            }
        } catch (PartInitException e) {
            FileSyncPlugin.log("Failed to open url " + urlStr, e, IStatus.ERROR);
        } catch (MalformedURLException e) {
            FileSyncPlugin.log("Failed to open url " + urlStr, e, IStatus.ERROR);
        }
    }
}
