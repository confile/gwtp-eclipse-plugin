/**
 * Copyright 2013 ArcBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.arcbees.plugin.eclipse.wizard.createproject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.arcbees.plugin.eclipse.domain.Archetype;
import com.arcbees.plugin.eclipse.domain.ArchetypeCollection;
import com.arcbees.plugin.eclipse.domain.Category;
import com.arcbees.plugin.eclipse.domain.ProjectConfigModel;
import com.arcbees.plugin.eclipse.domain.Tag;
import com.arcbees.plugin.eclipse.util.ProgressTaskMonitor;

public class SelectArchetypePage extends WizardPage {
    private ProjectConfigModel projectConfigModel;
    private Table table;
    private TableViewer tableViewer;
    private ProgressTaskMonitor fetchMonitor;
    private boolean loading;

    public SelectArchetypePage(ProjectConfigModel projectConfigModel) {
        super("wizardPageSelectArchetype");

        this.projectConfigModel = projectConfigModel;

        setTitle("Select Archetype");
        setDescription("Select a project template to start with.");
    }

    /**
     * TODO cache results locally
     */
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        setPageComplete(false);

        runMonitor();
        runFetch();
    }

    // TODO extract to methods(s)
    private void runMonitor() {
        Job job = new Job("Fetching Archetypes...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                String doing = "Fetching Archetyes...";

                monitor.beginTask(doing, 100);
                fetchMonitor.beginTask(doing, 100);

                loading = true;
                do {
                    try {
                        TimeUnit.MILLISECONDS.sleep(25);

                        monitor.worked(1);
                        fetchMonitor.worked(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        loading = false;
                        return Status.CANCEL_STATUS;
                    }
                } while (loading);

                if (!loading) {
                    fetchMonitor.reset();
                }

                return Status.OK_STATUS;
            }
        };
        job.schedule();
    }

    // TODO extract to methods(s)
    private void runFetch() {
        Job job = new Job("Fetch Request") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                FetchArchetypes fetch = new FetchArchetypes();
                ArchetypeCollection collection = fetch.fetchArchetypes();
                if (collection != null) {
                    final List<Archetype> archetypes = collection.getArchetypes();
                    if (archetypes != null) {
                        Display.getDefault().asyncExec(new Runnable() {
                            @Override
                            public void run() {
                                tableViewer.setInput(archetypes);
                            }
                        });
                    }
                }

                loading = false;
                return Status.OK_STATUS;
            }
        };
        job.schedule();
    }

    // TODO add categories, sort GWTP first
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new GridLayout(1, false));

        tableViewer = new TableViewer(container, SWT.BORDER | SWT.FULL_SELECTION);
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        table = tableViewer.getTable();
        GridData gd_table = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_table.widthHint = 520;
        gd_table.heightHint = 420;
        table.setLayoutData(gd_table);
        table.setLinesVisible(true);
        table.setHeaderVisible(true);

        TableViewerColumn tableViewerColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        TableColumn tblclmnName = tableViewerColumn.getColumn();
        tblclmnName.setWidth(193);
        tblclmnName.setText("Name");

        TableViewerColumn tableViewerColumnCategories = new TableViewerColumn(tableViewer, SWT.NONE);
        TableColumn tblclmnCategories = tableViewerColumnCategories.getColumn();
        tblclmnCategories.setWidth(100);
        tblclmnCategories.setText("Categories");

        TableViewerColumn tableViewerColumnTag = new TableViewerColumn(tableViewer, SWT.NONE);
        TableColumn tblclmnTags = tableViewerColumnTag.getColumn();
        tblclmnTags.setWidth(409);
        tblclmnTags.setText("Tags");

        ProgressBar progressBarUi = new ProgressBar(container, SWT.SMOOTH);
        progressBarUi.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        fetchMonitor = new ProgressTaskMonitor(progressBarUi);

        tableViewerColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                Archetype a = (Archetype) element;
                return a.getName();
            }
        });

        // TODO deal with nulls
        tableViewerColumnTag.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                Archetype a = (Archetype) element;
                List<Tag> tags = a.getTags();
                StringBuffer sb = new StringBuffer();
                if (tags != null) {
                    for (int i = 0; i < tags.size(); i++) {
                        Tag t = tags.get(i);
                        sb.append(t.getName());
                        if (i < tags.size() - 1) {
                            sb.append(", ");
                        }
                    }
                }
                return sb.toString();
            }
        });

        // TODO deal with nulls
        tableViewerColumnCategories.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                Archetype a = (Archetype) element;
                List<Category> categories = a.getCategories();
                StringBuffer sb = new StringBuffer();
                if (categories != null) {
                    for (int i = 0; i < categories.size(); i++) {
                        Category c = categories.get(i);
                        sb.append(c.getName());
                        if (i < categories.size() - 1) {
                            sb.append(", ");
                        }
                    }
                }
                return sb.toString();
            }
        });

        Link link = new Link(container, SWT.NONE);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String surl = "https://project-directory.appspot.com/";
                gotoUrl(surl);
            }
        });
        link.setText("<a>View this in the web app</a>");

        tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                Archetype archetypeSelected = (Archetype) selection.getFirstElement();
                projectConfigModel.seArchetypeSelected(archetypeSelected);
                setPageComplete(true);
            }
        });
    }

    /**
     * Open url in default external browser
     */
    private void gotoUrl(String surl) {
        try {
            URL url = new URL(surl);
            PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(url);
        } catch (PartInitException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
    }

}
