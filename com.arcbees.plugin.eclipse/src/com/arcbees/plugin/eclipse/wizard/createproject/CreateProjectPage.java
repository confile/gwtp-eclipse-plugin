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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.observable.ChangeEvent;
import org.eclipse.core.databinding.observable.IChangeListener;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.databinding.fieldassist.ControlDecorationSupport;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wb.swt.ResourceManager;

import com.arcbees.plugin.eclipse.domain.ProjectConfigModel;
import com.arcbees.plugin.eclipse.validators.ModuleNameValidator;
import com.arcbees.plugin.eclipse.validators.NewProjectArtifactIdValidator;
import com.arcbees.plugin.eclipse.validators.PackageNameValidator;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormAttachment;
import swing2swt.layout.FlowLayout;

public class CreateProjectPage extends WizardPage {
    private DataBindingContext m_bindingContext;
    private Text packageName;
    private Text newProjectPath;
    private Text moduleName;
    private Text groupId;
    private Text artifactId;
    private ProjectConfigModel projectConfigModel;

    public CreateProjectPage(ProjectConfigModel projectConfigModel) {
        super("wizardPageCreateProject");

        this.projectConfigModel = projectConfigModel;

        setMessage("Create a GWT-Platform project.");
        setPageComplete(false);

        setImageDescriptor(ResourceManager.getPluginImageDescriptor("com.arcbees.plugin.eclipse", "icons/logo.png"));
        setTitle("GWTP Project Creation");
        setDescription("Create a GWT-Platform project.");
    }

    public void createControl(final Composite parent) {
        Composite container = new Composite(parent, SWT.NULL);

        setControl(container);
        container.setLayout(new FillLayout(SWT.VERTICAL));

        Group grpMaven = new Group(container, SWT.NONE);
        grpMaven.setText("Maven");
        grpMaven.setLayout(new GridLayout(1, false));

        Label lblArtifactid = new Label(grpMaven, SWT.NONE);
        lblArtifactid.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        lblArtifactid.setText("ArtifactId: 'myproject'");

        artifactId = new Text(grpMaven, SWT.BORDER);
        artifactId.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        artifactId.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                checkProjectName();
            }
        });

        Label lblGroupid = new Label(grpMaven, SWT.NONE);
        lblGroupid.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        lblGroupid.setText("GroupId: 'com.arcbees.project'");

        groupId = new Text(grpMaven, SWT.BORDER);
        groupId.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));

        Group grpJavaProject = new Group(container, SWT.NONE);
        grpJavaProject.setLayout(new GridLayout(1, false));
        grpJavaProject.setText("Java Project");

        Label lblNewLabel = new Label(grpJavaProject, SWT.NONE);
        GridData gd_lblNewLabel = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_lblNewLabel.widthHint = 556;
        lblNewLabel.setLayoutData(gd_lblNewLabel);
        lblNewLabel.setText("GWT Module Name: 'Project'");

        moduleName = new Text(grpJavaProject, SWT.BORDER);
        moduleName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label lblPackageName = new Label(grpJavaProject, SWT.NONE);
        lblPackageName.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        lblPackageName.setText("Package Name: 'com.arcbees.project'");

        packageName = new Text(grpJavaProject, SWT.BORDER);
        packageName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Group grpLocation = new Group(container, SWT.NONE);
        grpLocation.setText("Workspace");
        grpLocation.setLayout(new GridLayout(1, false));

        Label lblNewProjectPath = new Label(grpLocation, SWT.NONE);
        lblNewProjectPath.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, true, false, 1, 1));
        lblNewProjectPath.setText("New Project Path");

        newProjectPath = new Text(grpLocation, SWT.BORDER);
        newProjectPath.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        newProjectPath.setEnabled(false);

        Group group = new Group(container, SWT.NONE);

        group.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

        Composite composite = new Composite(group, SWT.NONE);
        composite.setLayout(new GridLayout(2, false));

        Button btnHint = new Button(composite, SWT.NONE);
        btnHint.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                hintFillIn();
            }
        });
        btnHint.setText("Hint");

        Link link = new Link(composite, SWT.NONE);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String surl = "https://github.com/ArcBees/gwtp-eclipse-plugin/wiki/Project-Creation";
                gotoUrl(surl);
            }
        });
        link.setText("<a>Project Creation Help</a>");
        m_bindingContext = initDataBindings();

        // Observe input changes and add validator decorators
        observeBindingChanges();
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

    private void observeBindingChanges() {
        IObservableList bindings = m_bindingContext.getValidationStatusProviders();
        for (Object o : bindings) {
            Binding binding = (Binding) o;

            // Validator feedback control
            ControlDecorationSupport.create(binding, SWT.TOP | SWT.LEFT);

            binding.getTarget().addChangeListener(new IChangeListener() {
                @Override
                public void handleChange(ChangeEvent event) {
                    checkBindingValidationStatus();
                }
            });
        }
    }

    private void checkProjectName() {
        String projectName = artifactId.getText();

        // No zero length property names
        if (projectName.trim().length() == 0) {
            return;
        }

        checkIfProjectExist(projectName);

        // setup the project path
        String workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toString();
        String projectPath = workspacePath + File.separator + projectName;

        projectConfigModel.setWorkspacePath(workspacePath);
        projectConfigModel.setProjectPath(projectPath);
        newProjectPath.setText(projectPath);
    }

    private boolean checkIfProjectExist(String projectName) {
        // Pre-setup the project for checking
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

        // Display error when project exists or remove error when it doesn't
        if (project.exists()) {
            setMessage("The '" + projectName + "' project name already exists.", IMessageProvider.ERROR);
        } else {
            setMessage(null);
        }

        return project.exists();
    }

    /**
     * Check all the bindings validators for OK status.
     */
    private void checkBindingValidationStatus() {
        IObservableList bindings = m_bindingContext.getValidationStatusProviders();

        boolean success = true;
        for (Object o : bindings) {
            Binding b = (Binding) o;
            IObservableValue status = b.getValidationStatus();
            IStatus istatus = (IStatus) status.getValue();
            if (!istatus.isOK()) {
                success = false;
            }
        }

        // All statuses passed, enable next button.
        setPageComplete(success);
    }

    private void hintFillIn() {
        packageName.setText("com.arcbees.project");
        moduleName.setText("Project");
        groupId.setText("com.arcbees.project");
        artifactId.setText("myproject");

        projectConfigModel.setPackageName(packageName.getText());
        projectConfigModel.setModuleName(moduleName.getText());
        projectConfigModel.setGroupId(groupId.getText());
        projectConfigModel.setArtifactId(artifactId.getText());

        checkProjectName();
    }

    protected DataBindingContext initDataBindings() {
        DataBindingContext bindingContext = new DataBindingContext();
        //
        IObservableValue observeTextArtifactIdObserveWidget = WidgetProperties.text(SWT.Modify).observe(artifactId);
        IObservableValue artifactIdProjectConfigModelObserveValue = BeanProperties.value("artifactId").observe(
                projectConfigModel);
        UpdateValueStrategy strategy = new UpdateValueStrategy();
        strategy.setBeforeSetValidator(new NewProjectArtifactIdValidator());
        bindingContext.bindValue(observeTextArtifactIdObserveWidget, artifactIdProjectConfigModelObserveValue,
                strategy, null);
        //
        IObservableValue observeTextGroupIdObserveWidget = WidgetProperties.text(SWT.Modify).observe(groupId);
        IObservableValue groupIdProjectConfigModelObserveValue = BeanProperties.value("groupId").observe(
                projectConfigModel);
        UpdateValueStrategy strategy_1 = new UpdateValueStrategy();
        strategy_1.setBeforeSetValidator(new PackageNameValidator());
        bindingContext.bindValue(observeTextGroupIdObserveWidget, groupIdProjectConfigModelObserveValue, strategy_1,
                null);
        //
        IObservableValue observeTextModuleNameObserveWidget = WidgetProperties.text(SWT.Modify).observe(moduleName);
        IObservableValue moduleNameProjectConfigModelObserveValue = BeanProperties.value("moduleName").observe(
                projectConfigModel);
        UpdateValueStrategy strategy_2 = new UpdateValueStrategy();
        strategy_2.setBeforeSetValidator(new ModuleNameValidator());
        bindingContext.bindValue(observeTextModuleNameObserveWidget, moduleNameProjectConfigModelObserveValue,
                strategy_2, null);
        //
        IObservableValue observeTextPackageNameObserveWidget = WidgetProperties.text(SWT.Modify).observe(packageName);
        IObservableValue packageNameProjectConfigModelObserveValue = BeanProperties.value("packageName").observe(
                projectConfigModel);
        UpdateValueStrategy strategy_3 = new UpdateValueStrategy();
        strategy_3.setBeforeSetValidator(new PackageNameValidator());
        bindingContext.bindValue(observeTextPackageNameObserveWidget, packageNameProjectConfigModelObserveValue, null,
                strategy_3);
        //
        return bindingContext;
    }
}
