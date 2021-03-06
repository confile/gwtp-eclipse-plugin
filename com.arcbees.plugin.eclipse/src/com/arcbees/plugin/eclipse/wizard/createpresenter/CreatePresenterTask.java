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

package com.arcbees.plugin.eclipse.wizard.createpresenter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.core.ResolvedSourceType;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import com.arcbees.plugin.eclipse.domain.PresenterConfigModel;
import com.arcbees.plugin.eclipse.util.CodeFormattingUtil;
import com.arcbees.plugin.eclipse.util.PackageHierarchy;
import com.arcbees.plugin.eclipse.util.PackageHierarchyElement;
import com.arcbees.plugin.template.create.place.CreateNameTokens;
import com.arcbees.plugin.template.create.presenter.CreateNestedPresenter;
import com.arcbees.plugin.template.create.presenter.CreatePopupPresenter;
import com.arcbees.plugin.template.create.presenter.CreatePresenterWidget;
import com.arcbees.plugin.template.domain.place.CreatedNameTokens;
import com.arcbees.plugin.template.domain.place.NameToken;
import com.arcbees.plugin.template.domain.place.NameTokenOptions;
import com.arcbees.plugin.template.domain.presenter.CreatedNestedPresenter;
import com.arcbees.plugin.template.domain.presenter.CreatedPopupPresenter;
import com.arcbees.plugin.template.domain.presenter.CreatedPresenterWidget;
import com.arcbees.plugin.template.domain.presenter.NestedPresenterOptions;
import com.arcbees.plugin.template.domain.presenter.PopupPresenterOptions;
import com.arcbees.plugin.template.domain.presenter.PresenterOptions;
import com.arcbees.plugin.template.domain.presenter.PresenterWidgetOptions;
import com.arcbees.plugin.template.domain.presenter.RenderedTemplate;

public class CreatePresenterTask {
    public final static Logger logger = Logger.getLogger(CreatePresenterTask.class.getName());

    public static CreatePresenterTask run(PresenterConfigModel presenterConfigModel, IProgressMonitor progressMonitor) {
        CreatePresenterTask createPresenterTask = new CreatePresenterTask(presenterConfigModel, progressMonitor);
        createPresenterTask.run();
        return createPresenterTask;
    }

    private PresenterConfigModel presenterConfigModel;
    private IProgressMonitor progressMonitor;
    private IPackageFragment presenterCreatedPackage;
    private PackageHierarchy packageHierarchy;
    private IPackageFragment createdNameTokensPackage;
    private CodeFormattingUtil codeFormatter;
    private CreatedNestedPresenter createdNestedPresenterTemplates;
    private CreatedPresenterWidget createdPresenterWidgetTemplates;
    private CreatedPopupPresenter createdPopupPresenterTemplates;
    private CreatedNameTokens createdNameTokenTemplates;
    private boolean forceWriting = true;

    private CreatePresenterTask(PresenterConfigModel presenterConfigModel, IProgressMonitor progressMonitor) {
        this.presenterConfigModel = presenterConfigModel;
        this.progressMonitor = progressMonitor;

        codeFormatter = new CodeFormattingUtil(presenterConfigModel.getJavaProject(), progressMonitor);
    }

    private void run() {
        logger.info("Creating presenter started...");

        createPackageHierachyIndex();

        createNameTokensPackage();
        try {
            createNametokensFile();
        } catch (Exception e) {
            warn("Could not create or find the name tokens file 'NameTokens.java': Error: " + e.toString());
            e.printStackTrace();
            return;
        }

        try {
            fetchTemplatesNameTokens();
        } catch (Exception e) {
            warn("Could not fetch NameTokens templates: Error: " + e.toString());
            e.printStackTrace();
            return;
        }

        try {
            fetchPresenterTemplates();
        } catch (Exception e) {
            warn("Could not fetch the ntested presenter templates: Error: " + e.toString());
            e.printStackTrace();
            return;
        }

        createNameTokensFieldAndMethods();
        createPresenterPackage();
        createPresenterModule();
        createPresenterModuleLinkForGin();
        createPresenter();
        createPresenterUiHandlers();
        createPresenterView();
        createPresenterViewUi();
        createLinkPresenterWidgetToPanel();

        // TODO focus on new presenter package and open it up

        logger.info("...Creating presenter finished.");
    }

    private void createLinkPresenterWidgetToPanel() {
        if (!presenterConfigModel.getPresenterWidget()) {
            return;
        }

        // TODO add presenter widget to parent panel?
    }

    private void warn(final String message) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                MessageDialog.openWarning(presenterConfigModel.getShell(), "Warning", message);
            }
        });
    }

    private void createPackageHierachyIndex() {
        packageHierarchy = new PackageHierarchy(presenterConfigModel, progressMonitor);
        packageHierarchy.run();
    }

    private void fetchTemplatesNameTokens() throws Exception {
        if (!presenterConfigModel.getPlace()) {
            return;
        }

        NameToken token = new NameToken();
        token.setCrawlable(presenterConfigModel.getCrawlable());
        token.setToken(presenterConfigModel.getNameToken());

        List<NameToken> nameTokens = new ArrayList<NameToken>();
        nameTokens.add(token);

        NameTokenOptions nameTokenOptions = new NameTokenOptions();
        nameTokenOptions.setPackageName(createdNameTokensPackage.getElementName());
        nameTokenOptions.setNameTokens(nameTokens);

        boolean processFileOnly = false;
        try {
            createdNameTokenTemplates = CreateNameTokens.run(nameTokenOptions, true, processFileOnly);
        } catch (Exception e) {
            throw e;
        }
    }

    private void fetchPresenterTemplates() throws Exception {
        PresenterOptions presenterOptions = new PresenterOptions();
        presenterOptions.setPackageName(presenterConfigModel.getSelectedPackageAndNameAsSubPackage());
        presenterOptions.setName(presenterConfigModel.getName());
        presenterOptions.setOnbind(presenterConfigModel.getOnBind());
        presenterOptions.setOnhide(presenterConfigModel.getOnHide());
        presenterOptions.setOnreset(presenterConfigModel.getOnReset());
        presenterOptions.setOnunbind(presenterConfigModel.getOnUnbind());
        presenterOptions.setManualreveal(presenterConfigModel.getUseManualReveal());
        presenterOptions.setPrepareFromRequest(presenterConfigModel.getUsePrepareFromRequest());
        presenterOptions.setUihandlers(presenterConfigModel.getUseUiHandlers());
        
        // TODO future
        //presenterOptions.setGatekeeper(presenterConfigModel.getGatekeeper());

        if (presenterConfigModel.getNestedPresenter()) {
            fetchNestedTemplate(presenterOptions);
        } else if (presenterConfigModel.getPresenterWidget()) {
            fetchPresenterWidgetTemplate(presenterOptions);
        } else if (presenterConfigModel.getPopupPresenter()) {
            fetchPopupPresenterTemplate(presenterOptions);
        }
    }

    private void fetchNestedTemplate(PresenterOptions presenterOptions) throws Exception {
        NestedPresenterOptions nestedPresenterOptions = new NestedPresenterOptions();
        nestedPresenterOptions.setPlace(presenterConfigModel.getPlace());
        nestedPresenterOptions.setNameToken(presenterConfigModel.getNameToken());
        nestedPresenterOptions.setCrawlable(presenterConfigModel.getCrawlable());
        nestedPresenterOptions.setCodeSplit(presenterConfigModel.getCodeSplit());
        nestedPresenterOptions.setNameToken(presenterConfigModel.getNameTokenWithClass());
        nestedPresenterOptions.setNameTokenImport(presenterConfigModel.getNameTokenUnitImport());
        nestedPresenterOptions.setContentSlotImport(presenterConfigModel.getContentSlotImport());

        if (presenterConfigModel.getRevealInRoot()) {
            nestedPresenterOptions.setRevealType("Root");
        } else if (presenterConfigModel.getRevealInRootLayout()) {
            nestedPresenterOptions.setRevealType("RootLayout");
        } else if (presenterConfigModel.getPopupPresenter()) {
            nestedPresenterOptions.setRevealType("RootPopup");
        } else if (presenterConfigModel.getRevealInSlot()) {
            nestedPresenterOptions.setRevealType(presenterConfigModel.getContentSlotAsString());
        }

        try {
            createdNestedPresenterTemplates = CreateNestedPresenter.run(presenterOptions, nestedPresenterOptions, true);
        } catch (Exception e) {
            throw e;
        }
    }

    private void fetchPresenterWidgetTemplate(PresenterOptions presenterOptions) throws Exception {
        PresenterWidgetOptions presenterWidgetOptions = new PresenterWidgetOptions();
        presenterWidgetOptions.setSingleton(presenterConfigModel.getSingleton());

        try {
            createdPresenterWidgetTemplates = CreatePresenterWidget.run(presenterOptions, presenterWidgetOptions, true);
        } catch (Exception e) {
            throw e;
        }
    }

    private void fetchPopupPresenterTemplate(PresenterOptions presenterOptions) throws Exception {
        PopupPresenterOptions presenterWidgetOptions = new PopupPresenterOptions();
        presenterWidgetOptions.setSingleton(presenterConfigModel.getSingleton());
        presenterWidgetOptions.setCustom(presenterConfigModel.getOverridePopup());

        try {
            createdPopupPresenterTemplates = CreatePopupPresenter.run(presenterOptions, presenterWidgetOptions, true);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Create a sub package for the presenter classes
     */
    private String createPresenterPackage() {
        String presenterPackageName = presenterConfigModel.getSelectedPackageAndNameAsSubPackage();
        createPackage(presenterPackageName, forceWriting);
        logger.info("Created Package: " + presenterPackageName);
        return presenterPackageName;
    }

    private IPackageFragment createPackage(String packageName, boolean forceWriting) {
        IPackageFragment selectedPackage = presenterConfigModel.getSelectedPackage();
        IPackageFragmentRoot selectedPackageRoot = (IPackageFragmentRoot) selectedPackage.getParent();

        IPackageFragment created = null;
        try {
            created = presenterCreatedPackage = selectedPackageRoot.createPackageFragment(packageName, forceWriting,
                    progressMonitor);
        } catch (JavaModelException e) {
            warn("Could not create packageName=" + packageName + ": Error: " + e.toString());
            e.printStackTrace();
        }

        return created;
    }

    private void createNameTokensPackage() {
        if (!presenterConfigModel.getPlace()) {
            return;
        }

        IPackageFragment selectedPackage = presenterConfigModel.getSelectedPackage();
        String selectedPackageString = selectedPackage.getElementName();
        PackageHierarchyElement clientPackage = packageHierarchy.findParentClient(selectedPackageString);
        String clientPackageString = clientPackage.getPackageFragment().getElementName();

        // name tokens package ...client.place.NameTokens
        clientPackageString += ".place";

        PackageHierarchyElement nameTokensPackageExists = packageHierarchy.find(clientPackageString);

        if (nameTokensPackageExists != null && nameTokensPackageExists.getPackageFragment() != null) {
            createdNameTokensPackage = nameTokensPackageExists.getPackageFragment();
        } else {
            createdNameTokensPackage = createPackage(clientPackageString, forceWriting);
        }
    }

    private void createPresenterModule() {
        RenderedTemplate rendered = null;
        if (presenterConfigModel.getNestedPresenter()) {
            rendered = createdNestedPresenterTemplates.getModule();
        } else if (presenterConfigModel.getPresenterWidget()) {
            rendered = createdPresenterWidgetTemplates.getModule();
        } else if (presenterConfigModel.getPopupPresenter()) {
            rendered = createdPopupPresenterTemplates.getModule();
        }
        createClass(rendered, forceWriting);
    }

    /**
     * TODO extraction of functions, TODO extract "GinModule" to constant, TODO extract "gin" to constant
     */
    private void createPresenterModuleLinkForGin() {
        // 1. first search parent
        ICompilationUnit unit = packageHierarchy.findInterfaceTypeInParentPackage(
                presenterConfigModel.getSelectedPackage(), "GinModule");

        // 2. next check if the parent is client and if so, scan all packages for ginModule
        String selectedPackageElementName = presenterConfigModel.getSelectedPackage().getElementName();
        if (unit == null && packageHierarchy.isParentTheClientPackage(selectedPackageElementName)) {
            // first check for a gin package with GinModule
            PackageHierarchyElement hierarchyElement = packageHierarchy.findParentClientAndAddPackage(
                    selectedPackageElementName, "gin");
            if (hierarchyElement != null) {
                IPackageFragment clienPackage = hierarchyElement.getPackageFragment();
                unit = packageHierarchy.findInterfaceTypeInParentPackage(clienPackage, "GinModule");
            }

            // If no gin package check for any existence of a GinModule
            // TODO could make this smarter in the future, this is a last resort, to install it somewhere.
            if (unit == null) {
                unit = packageHierarchy.findFirstInterfaceType("GinModule");
                logger.info("Warning: This didn't find a ideal place to put the gin install for the new presenter module");
            }
        }

        // 3. walk up next parent for and look for gin module
        if (unit == null) {
            if (selectedPackageElementName.contains("client")) {
                PackageHierarchyElement hierarchyElement = packageHierarchy.findParent(selectedPackageElementName);

                if (hierarchyElement.getPackageFragment() != null) {
                    IPackageFragment parentParentPackage = hierarchyElement.getPackageFragment();
                    unit = packageHierarchy.findInterfaceTypeInParentPackage(parentParentPackage, "GinModule");
                }
            }
        }

        // 4. search all filter by GinModule interface, this would be easy
        // If no gin package check for any existence of a GinModule
        // TODO could make this smarter in the future, this is a last resort, to install it somewhere.
        if (unit == null) {
            unit = packageHierarchy.findFirstInterfaceType("GinModule");
            logger.info("Warning: This didn't find a ideal place to put the gin install for the new presenter module");
        }

        // (could do this next for ease)
        if (unit != null) {
            try {
                createPresenterGinlink(unit);
            } catch (JavaModelException e) {
                warn("Could not create gin link. Error1: " + e.toString());
                e.printStackTrace();
            } catch (MalformedTreeException e) {
                warn("Could not create gin link. Error2: " + e.toString());
                e.printStackTrace();
            } catch (BadLocationException e) {
                warn("Could not create gin link. Error3: " + e.toString());
                e.printStackTrace();
            }
        } else {
            logger.warning("Error: Wasn't able to install Module");
            warn("Could not create install module.");
        }
    }

    /**
     * TODO extract this possibly, but I think I'll wait till I get into slots before I do it see what is common.
     */
    private void createPresenterGinlink(ICompilationUnit unit) throws JavaModelException, MalformedTreeException,
            BadLocationException {
        Document document = new Document(unit.getSource());

        CompilationUnit astRoot = initAstRoot(unit);

        // creation of ASTRewrite
        ASTRewrite rewrite = ASTRewrite.create(astRoot.getAST());

        // find the configure method
        MethodDeclaration method = findMethod(astRoot, "configure");
        if (method == null) {
            warn("Wasn't able to findMethod Configure in unit: " + unit.getElementName());
            logger.severe("createPresenterGinLink() unit did not have configure implementation.");
            return;
        }

        // presenter import
        String fileNameForModule = getFileNameForModule();
        String importName = presenterConfigModel.getSelectedPackageAndNameAsSubPackage() + "." + fileNameForModule;
        String[] presenterPackage = importName.split("\\.");
        ImportDeclaration importDeclaration = astRoot.getAST().newImportDeclaration();
        importDeclaration.setName(astRoot.getAST().newName(presenterPackage));
        ListRewrite lrw = rewrite.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
        lrw.insertLast(importDeclaration, null);

        // presenter configure method install(new Module());
        String moduleName = fileNameForModule + "()";
        String installModuleStatement = "install(new " + moduleName + ");";

        Block block = method.getBody();
        ListRewrite listRewrite = rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
        ASTNode placeHolder = rewrite.createStringPlaceholder(installModuleStatement, ASTNode.EMPTY_STATEMENT);
        listRewrite.insertFirst(placeHolder, null);

        // computation of the text edits
        TextEdit edits = rewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));

        // computation of the new source code
        edits.apply(document);
        String newSource = document.get();

        // update of the compilation unit and save it
        IBuffer buffer = unit.getBuffer();
        buffer.setContents(newSource);
        buffer.save(progressMonitor, forceWriting);

        logger.info("Added presenter gin install into " + unit.getElementName() + " " + installModuleStatement);
    }

    private String getFileNameForModule() {
        String name = null;
        if (presenterConfigModel.getNestedPresenter()) {
            name = createdNestedPresenterTemplates.getModule().getNameAndNoExts();
        } else if (presenterConfigModel.getPresenterWidget()) {
            name = createdPresenterWidgetTemplates.getModule().getNameAndNoExts();
        } else if (presenterConfigModel.getPopupPresenter()) {
            name = createdPopupPresenterTemplates.getModule().getNameAndNoExts();
        }
        return name;
    }

    private MethodDeclaration findMethod(CompilationUnit astRoot, String methodName) {
        MethodDeclaration[] methods = ((TypeDeclaration) astRoot.types().get(0)).getMethods();
        if (methods == null) {
            return null;
        }

        for (MethodDeclaration method : methods) {
            if (method.getName().toString().contains(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Creation of DOM/AST from a ICompilationUnit.
     */
    private CompilationUnit initAstRoot(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setSource(unit);
        CompilationUnit astRoot = (CompilationUnit) parser.createAST(progressMonitor);
        return astRoot;
    }

    private void createPresenter() {
        RenderedTemplate rendered = null;
        if (presenterConfigModel.getNestedPresenter()) {
            rendered = createdNestedPresenterTemplates.getPresenter();
        } else if (presenterConfigModel.getPresenterWidget()) {
            rendered = createdPresenterWidgetTemplates.getPresenter();
        } else if (presenterConfigModel.getPopupPresenter()) {
            rendered = createdPopupPresenterTemplates.getPresenter();
        }
        createClass(rendered, forceWriting);
    }

    private void createPresenterUiHandlers() {
        if (!presenterConfigModel.getUseUiHandlers()) {
            return;
        }
        RenderedTemplate rendered = null;
        if (presenterConfigModel.getNestedPresenter()) {
            rendered = createdNestedPresenterTemplates.getUihandlers();
        } else if (presenterConfigModel.getPresenterWidget()) {
            rendered = createdPresenterWidgetTemplates.getUihandlers();
        } else if (presenterConfigModel.getPopupPresenter()) {
            rendered = createdPopupPresenterTemplates.getUihandlers();
        }
        createClass(rendered, forceWriting);
    }

    private void createPresenterView() {
        RenderedTemplate rendered = null;
        if (presenterConfigModel.getNestedPresenter()) {
            rendered = createdNestedPresenterTemplates.getView();
        } else if (presenterConfigModel.getPresenterWidget()) {
            rendered = createdPresenterWidgetTemplates.getView();
        } else if (presenterConfigModel.getPopupPresenter()) {
            rendered = createdPopupPresenterTemplates.getView();
        }
        createClass(rendered, forceWriting);
    }

    private void createPresenterViewUi() {
        RenderedTemplate rendered = null;
        if (presenterConfigModel.getNestedPresenter()) {
            rendered = createdNestedPresenterTemplates.getViewui();
        } else if (presenterConfigModel.getPresenterWidget()) {
            rendered = createdPresenterWidgetTemplates.getViewui();
        } else if (presenterConfigModel.getPopupPresenter()) {
            rendered = createdPopupPresenterTemplates.getViewui();
        }

        IFolder folder = (IFolder) presenterCreatedPackage.getResource();
        IFile newFile = folder.getFile(rendered.getNameAndNoExt());

        byte[] bytes = rendered.getContents().getBytes();
        InputStream source = new ByteArrayInputStream(bytes);
        try {
            newFile.create(source, IResource.NONE, progressMonitor);
        } catch (CoreException e) {
            warn("Could not create source createPresenterViewUi(). Error: " + e.toString());
            e.printStackTrace();
        }
    }

    private void createNametokensFile() throws Exception {
        if (!presenterConfigModel.getPlace()) {
            return;
        }

        // look for existing name tokens first.
        List<ResolvedSourceType> foundNameTokens = packageHierarchy.findClassName("NameTokens");

        ICompilationUnit unitNameTokens = null;
        if (foundNameTokens != null && foundNameTokens.size() > 0) {
            ResolvedSourceType foundNameTokensSource = foundNameTokens.get(0);
            unitNameTokens = foundNameTokensSource.getCompilationUnit();
        } else {
            unitNameTokens = createNewNameTokensFile();
        }

        if (unitNameTokens == null) {
            warn("Could not create NameTokens.java");
            return;
        }

        // used for import string
        presenterConfigModel.setNameTokenUnit(unitNameTokens);
    }

    /**
     * create name tokens class, if it doesn't exist
     */
    private void createNameTokensFieldAndMethods() {
        if (!presenterConfigModel.getPlace()) {
            return;
        }
        ICompilationUnit unitNameTokens = presenterConfigModel.getNameTokenUnit();
        if (unitNameTokens == null) {
            logger.info("createNameTokensFieldAndMethods: skipping creating nametokens methods.");
            return;
        }

        try {
            addMethodsToNameTokens(unitNameTokens);
        } catch (JavaModelException e) {
            warn("Could not createnNameTokenFieldAndMethods Error1: " + e.toString());
            e.printStackTrace();
        } catch (MalformedTreeException e) {
            warn("Could not createnNameTokenFieldAndMethods Error2: " + e.toString());
            e.printStackTrace();
        } catch (BadLocationException e) {
            warn("Could not createnNameTokenFieldAndMethods Error3: " + e.toString());
            e.printStackTrace();
        }
    }

    private void addMethodsToNameTokens(ICompilationUnit unit) throws JavaModelException, MalformedTreeException,
            BadLocationException {
        Document document = new Document(unit.getSource());
        CompilationUnit astRoot = initAstRoot(unit);

        // creation of ASTRewrite
        ASTRewrite rewrite = ASTRewrite.create(astRoot.getAST());

        // find existing method
        MethodDeclaration method = findMethod(astRoot, presenterConfigModel.getNameTokenMethodName());
        if (method != null) {
            warn("FYI: the method in nameTokens already exists." + method.toString());
            return;
        }

        List<String> fields = createdNameTokenTemplates.getFields();
        List<String> methods = createdNameTokenTemplates.getMethods();
        String fieldSource = fields.get(0);
        String methodSource = methods.get(0);

        List types = astRoot.types();
        ASTNode rootNode = (ASTNode) types.get(0);
        ListRewrite listRewrite = rewrite.getListRewrite(rootNode, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

        ASTNode fieldNode = rewrite.createStringPlaceholder(fieldSource, ASTNode.EMPTY_STATEMENT);
        ASTNode methodNode = rewrite.createStringPlaceholder(methodSource, ASTNode.EMPTY_STATEMENT);

        listRewrite.insertFirst(fieldNode, null);
        listRewrite.insertLast(methodNode, null);

        // computation of the text edits
        TextEdit edits = rewrite.rewriteAST(document, unit.getJavaProject().getOptions(true));

        // computation of the new source code
        edits.apply(document);

        // format code
        String newSource = codeFormatter.formatCodeJavaClass(document);

        // update of the compilation unit and save it
        IBuffer buffer = unit.getBuffer();
        buffer.setContents(newSource);
        buffer.save(progressMonitor, forceWriting);
    }

    private ICompilationUnit createNewNameTokensFile() throws Exception {
        boolean processFileOnly = true;
        NameTokenOptions nameTokenOptions = new NameTokenOptions();
        nameTokenOptions.setPackageName(createdNameTokensPackage.getElementName());
        CreatedNameTokens createdNameToken;
        try {
            createdNameToken = CreateNameTokens.run(nameTokenOptions, true, processFileOnly);
        } catch (Exception e1) {
            throw e1;
        }

        RenderedTemplate rendered = createdNameToken.getNameTokensFile();
        String className = rendered.getNameAndNoExt();
        String contents = rendered.getContents();

        ICompilationUnit nameTokenUnit = null;
        try {
            nameTokenUnit = createdNameTokensPackage.createCompilationUnit(className, contents, forceWriting,
                    progressMonitor);
        } catch (JavaModelException e) {
            logger.warning("Couldn't create className: " + className);
            warn("Could not createNameTokensFile Error: " + e.toString());
            e.printStackTrace();
        }

        return nameTokenUnit;
    }

    private void createClass(RenderedTemplate rendered, boolean force) {
        String className = rendered.getNameAndNoExt();
        String contents = rendered.getContents();

        ICompilationUnit unit = null;
        try {
            unit = presenterCreatedPackage.createCompilationUnit(className, contents, force, progressMonitor);
        } catch (JavaModelException e) {
            warn("Could create class. " + className + " Error: " + e.toString());
            logger.warning("Couldn't create className: " + className);
            e.printStackTrace();
            return;
        }

        try {
            codeFormatter.formatCodeJavaClassAndSaveIt(unit, forceWriting);
        } catch (JavaModelException e) {
            warn("Could format class. " + className + " Error: " + e.toString());
            e.printStackTrace();
        }

        codeFormatter.organizeImports(unit);
    }
}
