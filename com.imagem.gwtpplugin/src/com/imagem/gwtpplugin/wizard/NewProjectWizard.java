/**
 * Copyright 2011 Les Syst�mes M�dicaux Imagem Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.imagem.gwtpplugin.wizard;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchGroup;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import com.google.appengine.eclipse.core.nature.GaeNature;
import com.google.appengine.eclipse.core.sdk.GaeSdkContainer;
import com.google.appengine.eclipse.core.validators.GaeProjectValidator;
import com.google.gdt.eclipse.core.validators.WebAppProjectValidator;
import com.google.gdt.eclipse.suite.launch.WebAppLaunchUtil;
import com.google.gwt.eclipse.core.nature.GWTNature;
import com.google.gwt.eclipse.core.runtime.GWTRuntimeContainer;
import com.imagem.gwtpplugin.Activator;
import com.imagem.gwtpplugin.projectfile.src.GwtXmlModule;
import com.imagem.gwtpplugin.projectfile.src.Jdoconfig;
import com.imagem.gwtpplugin.projectfile.src.Log4j;
import com.imagem.gwtpplugin.projectfile.src.client.EntryPoint;
import com.imagem.gwtpplugin.projectfile.src.client.gin.Ginjector;
import com.imagem.gwtpplugin.projectfile.src.client.gin.PresenterModule;
import com.imagem.gwtpplugin.projectfile.src.client.place.PlaceAnnotation;
import com.imagem.gwtpplugin.projectfile.src.client.place.PlaceManager;
import com.imagem.gwtpplugin.projectfile.src.client.place.Tokens;
import com.imagem.gwtpplugin.projectfile.src.server.guice.GuiceServletContextListener;
import com.imagem.gwtpplugin.projectfile.src.server.guice.HandlerModule;
import com.imagem.gwtpplugin.projectfile.src.server.guice.ServletModule;
import com.imagem.gwtpplugin.projectfile.war.AppengineWebXml;
import com.imagem.gwtpplugin.projectfile.war.Jar;
import com.imagem.gwtpplugin.projectfile.war.Logging;
import com.imagem.gwtpplugin.projectfile.war.ProjectCSS;
import com.imagem.gwtpplugin.projectfile.war.ProjectHTML;
import com.imagem.gwtpplugin.projectfile.war.WebXml;

@SuppressWarnings("restriction")
public class NewProjectWizard extends Wizard implements INewWizard {
	
	private static final String AOPALLIANCE = "aopalliance";
	private static final String GIN = "gin-r137";
	private static final String GUICE = "guice-2.0";
	private static final String GUICE_SERVLET = "guice-servlet-2.0";
	//private static final String GWTP = "gwtp-0.4";
	private static final String GWTP = "gwtp-all-0.5";

	private NewProjectWizardPage page;

	public NewProjectWizard() {
		super();
		setNeedsProgressMonitor(true);
		setWindowTitle("New GWTP Project");

		try {
			URL url = new URL(Activator.getDefault().getBundle().getEntry("/"), "icons/gwtp-logo.png");
			setDefaultPageImageDescriptor(ImageDescriptor.createFromURL(url));
		}
		catch(MalformedURLException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void addPages() {
		page = new NewProjectWizardPage();
		addPage(page);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean performFinish() {
		try {
			// Project base creation
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(page.getProjectName());

			// Project location
			URI location = null;
			String workspace = ResourcesPlugin.getWorkspace().getRoot().getLocationURI().toString() + "/";
			if(page.getProjectLocation() != null && !workspace.equals(page.getProjectLocation().toString())) {
				location = page.getProjectLocation();
			}
			IProjectDescription description = project.getWorkspace().newProjectDescription(project.getName());
			description.setLocationURI(location);
			
			// Project natures and builders
			ICommand javaBuilder = description.newCommand();
			javaBuilder.setBuilderName(JavaCore.BUILDER_ID);
			
			ICommand webAppBuilder = description.newCommand();
			webAppBuilder.setBuilderName(WebAppProjectValidator.BUILDER_ID);
			
			ICommand gwtBuilder = description.newCommand();
			gwtBuilder.setBuilderName("com.google.gwt.eclipse.core.gwtProjectValidator"); // TODO use the BUILDER_UI field
			
			if(page.useGAE()) {
				ICommand gaeBuilder = description.newCommand();
				gaeBuilder.setBuilderName(GaeProjectValidator.BUILDER_ID);

				// TODO use the BUILDER_UI field
				ICommand enhancer = description.newCommand();
				enhancer.setBuilderName("com.google.appengine.eclipse.core.enhancerbuilder"); // TODO use the BUILDER_UI field
				
				description.setBuildSpec(new ICommand[] {javaBuilder, webAppBuilder, gwtBuilder, gaeBuilder, enhancer});
				description.setNatureIds(new String[]{JavaCore.NATURE_ID, GWTNature.NATURE_ID, GaeNature.NATURE_ID});
			}
			else {
				description.setBuildSpec(new ICommand[] {javaBuilder, webAppBuilder, gwtBuilder});
				description.setNatureIds(new String[]{JavaCore.NATURE_ID, GWTNature.NATURE_ID});
			}
	

			project.create(description, null); // TODO Progress Monitor
			if(!project.isOpen()) {
				project.open(null); // TODO Progress Monitor
			}
			
			// Java Project creation
			IJavaProject javaProject = JavaCore.create(project);
			
			// war/WEB-INF/lib folder creation
			IPath warPath = new Path("war");
			project.getFolder(warPath).create(false, true, null); // TODO Progress Monitor
			
			IPath webInfPath = warPath.append("WEB-INF");
			project.getFolder(webInfPath).create(false, true, null); // TODO Progress Monitor
			
			IPath libPath = webInfPath.append("lib");
			project.getFolder(libPath).create(false, true, null); // TODO Progress Monitor
			
			Jar aopallianceJar = new Jar(project, libPath, AOPALLIANCE);
			aopallianceJar.createFile();

			Jar ginJar = new Jar(project, libPath, GIN);
			ginJar.createFile();

			Jar guiceJar = new Jar(project, libPath, GUICE);
			guiceJar.createFile();

			Jar guiceServletJar = new Jar(project, libPath, GUICE_SERVLET);
			guiceServletJar.createFile();

			Jar gwtpJar = new Jar(project, libPath, GWTP);
			gwtpJar.createFile();
			
			// Classpath Entries creation
			List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();

			// Default output location
			IPath outputPath = new Path("/" + page.getProjectName()).append(webInfPath).append("classes");
			javaProject.setOutputLocation(outputPath, null);

			// Source folder
			IPath srcPath = new Path("src");
			project.getFolder(srcPath).create(false, true, null); // TODO Progress Monitor
			
			entries.add(JavaCore.newSourceEntry(javaProject.getPath().append("src")));

			// GWT SDK container
			IPath gwtContainer = GWTRuntimeContainer.CONTAINER_PATH;
			ClasspathContainerInitializer gwtInitializer = JavaCore.getClasspathContainerInitializer(gwtContainer.segment(0));
			gwtInitializer.initialize(gwtContainer, javaProject);
			entries.add(JavaCore.newContainerEntry(gwtContainer));

			// GAE SDK container
			if(page.useGAE()) {
				IPath gaeContainer = GaeSdkContainer.CONTAINER_PATH;
				ClasspathContainerInitializer gaeInitializer = JavaCore.getClasspathContainerInitializer(gaeContainer.segment(0));
				gaeInitializer.initialize(gaeContainer, javaProject);
				entries.add(JavaCore.newContainerEntry(gaeContainer));
			}

			// JRE container
			entries.addAll(Arrays.asList(PreferenceConstants.getDefaultJRELibrary()));

			// GWTP libs
			entries.add(JavaCore.newLibraryEntry(new Path("/" + javaProject.getElementName() + "/war/WEB-INF/lib/" + AOPALLIANCE + ".jar"), null, null));
			entries.add(JavaCore.newLibraryEntry(new Path("/" + javaProject.getElementName() + "/war/WEB-INF/lib/" + GIN + ".jar"), null, null));
			entries.add(JavaCore.newLibraryEntry(new Path("/" + javaProject.getElementName() + "/war/WEB-INF/lib/" + GUICE + ".jar"), null, null));
			entries.add(JavaCore.newLibraryEntry(new Path("/" + javaProject.getElementName() + "/war/WEB-INF/lib/" + GUICE_SERVLET + ".jar"), null, null));
			entries.add(JavaCore.newLibraryEntry(new Path("/" + javaProject.getElementName() + "/war/WEB-INF/lib/" + GWTP + ".jar"), null, null));

			javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[entries.size()]), null); // TODO Progress Monitor
			
			IPackageFragmentRoot root = javaProject.findPackageFragmentRoot(javaProject.getPath().append("src"));
			
			// TODO Create settings
			
			// Create src Folder
			if(page.useGAE()) {
				Log4j log4j = new Log4j(root);
				log4j.createFile();

				IPath metaInfPath = srcPath.append("META-INF");
				project.getFolder(metaInfPath).create(false, true, null); // TODO Progress Monitor

				Jdoconfig jdoconfig = new Jdoconfig(root);
				jdoconfig.createFile();
			}
			
			IPackageFragment projectPackage = root.createPackageFragment(page.getProjectPackage(), false, null); // TODO Progress Monitor
			
			// Client package
			IPackageFragment clientPackage = root.createPackageFragment(projectPackage.getElementName() + ".client", false, null); // TODO Progress Monitor
			
			root.createPackageFragment(clientPackage.getElementName() + ".core", false, null); // TODO Progress Monitor
			
			root.createPackageFragment(clientPackage.getElementName() + ".event", false, null); // TODO Progress Monitor
			
			// Place package
			IPackageFragment placePackage = root.createPackageFragment(clientPackage.getElementName() + ".place", false, null); // TODO Progress Monitor
			
			PlaceAnnotation defaultPlace = new PlaceAnnotation(root, placePackage.getElementName(), "DefaultPlace");
			
			PlaceManager placeManager = new PlaceManager(root, placePackage.getElementName(), "ClientPlaceManager");
			IField defaultPlaceField = placeManager.createPlaceRequestField(defaultPlace.getType());
			placeManager.createConstructor(new IType[]{defaultPlace.getType()}, new IField[]{defaultPlaceField});
			placeManager.createRevealDefaultPlaceMethod(defaultPlaceField);
			
			Tokens tokens = new Tokens(root, placePackage.getElementName(), "NameTokens");
			
			// Gin package
			IPackageFragment ginPackage = root.createPackageFragment(clientPackage.getElementName() + ".gin", false, null); // TODO Progress Monitor
			
			PresenterModule presenterModule = new PresenterModule(root, ginPackage.getElementName(), "ClientModule");
			presenterModule.createConfigureMethod(placeManager.getType());
			
			Ginjector ginjector = new Ginjector(root, ginPackage.getElementName(), "ClientGinjector", presenterModule.getType());
			ginjector.createDefaultGetterMethods();
			
			// Client package contents
			EntryPoint entryPoint = new EntryPoint(root, clientPackage.getElementName(), page.getProjectName());
			entryPoint.createGinjectorField(ginjector.getType());
			entryPoint.createOnModuleLoadMethod();
			
			// Project package contents
			GwtXmlModule gwtXmlModule = new GwtXmlModule(root, projectPackage.getElementName(), page.getProjectName());
			gwtXmlModule.createFile(entryPoint.getType(), ginjector.getType());
			
			// Server package
			IPackageFragment serverPackage = root.createPackageFragment(projectPackage.getElementName() + ".server", false, null); // TODO Progress Monitor
			
			// Guice package
			IPackageFragment guicePackage = root.createPackageFragment(serverPackage.getElementName() + ".guice", false, null); // TODO Progress Monitor
			
			ServletModule servletModule = new ServletModule(root, guicePackage.getElementName(), "DispatchServletModule");
			servletModule.createConfigureServletsMethod();
			
			HandlerModule handlerModule = new HandlerModule(root, guicePackage.getElementName(), "ServerModule");
			handlerModule.createConfigureHandlersMethod();
			
			GuiceServletContextListener guiceServletContextListener = new GuiceServletContextListener(root, guicePackage.getElementName(), "GuiceServletConfig");
			guiceServletContextListener.createInjectorGetterMethod(handlerModule.getType(), servletModule.getType());
			
			// Handler package
			root.createPackageFragment(serverPackage.getElementName() + ".handler", false, null); // TODO Progress Monitor
			
			// Shared package
			IPackageFragment sharedPackage = root.createPackageFragment(projectPackage.getElementName() + ".shared", false, null); // TODO Progress Monitor
			
			root.createPackageFragment(sharedPackage.getElementName() + ".action", false, null); // TODO Progress Monitor
			root.createPackageFragment(sharedPackage.getElementName() + ".model", false, null); // TODO Progress Monitor
			
			// Commit
			presenterModule.commit();
			ginjector.commit();
			defaultPlace.commit();
			placeManager.commit();
			tokens.commit();
			entryPoint.commit();
			
			servletModule.commit();
			handlerModule.commit();
			guiceServletContextListener.commit();
			
			// war contents
			ProjectHTML projectHTML = new ProjectHTML(project, warPath, project.getName());
			projectHTML.createFile();
			
			ProjectCSS projectCSS = new ProjectCSS(project, warPath, project.getName());
			projectCSS.createFile();
			
			// war/WEB-INF contents
			WebXml webXml = new WebXml(project, webInfPath);
			webXml.createFile(projectHTML.getFile(), guiceServletContextListener.getType());
			
			if(page.useGAE()) {
				AppengineWebXml appengineWebXml = new AppengineWebXml(project, warPath);
				appengineWebXml.createFile();

				Logging logging = new Logging(project, warPath);
				logging.createFile();
			}
			
			// Launch Config
			ILaunchConfigurationWorkingCopy launchConfig = WebAppLaunchUtil.createLaunchConfigWorkingCopy(project.getName(), project, WebAppLaunchUtil.determineStartupURL(project, false), false);
			ILaunchGroup[] groups = DebugUITools.getLaunchGroups();

			ArrayList groupsNames = new ArrayList();
			for(ILaunchGroup group : groups) {
				if((!("org.eclipse.debug.ui.launchGroup.debug".equals(group.getIdentifier()))) && (!("org.eclipse.debug.ui.launchGroup.run".equals(group.getIdentifier()))))
					continue;
				groupsNames.add(group.getIdentifier());
			}

			launchConfig.setAttribute("org.eclipse.debug.ui.favoriteGroups", groupsNames);
			launchConfig.doSave();
		}
		catch(Exception e) {
			return false;
		}
		return true;
	}
	
	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {}

}
