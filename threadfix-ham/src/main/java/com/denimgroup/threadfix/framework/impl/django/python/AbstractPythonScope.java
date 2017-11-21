package com.denimgroup.threadfix.framework.impl.django.python;

import javax.annotation.Nonnull;
import java.util.*;

import static com.denimgroup.threadfix.CollectionUtils.list;
import static com.denimgroup.threadfix.CollectionUtils.map;

public abstract class AbstractPythonScope {

    private AbstractPythonScope parentScope;
    private List<AbstractPythonScope> childScopes = list();
    private String sourceCodePath;
    private int sourceCodeLine;
    private Map<String, String> imports = map();
    private Map<String, String> urlsModifications = map();
    private int indentationLevel = -1;

    public abstract String getName();

    public PythonValue evaluate(String parameters) {
        return null;
    }

    public void setSourceCodePath(String sourceCodePath) {
        this.sourceCodePath = sourceCodePath;
    }

    public String getSourceCodePath() {
        return sourceCodePath;
    }

    public void setSourceCodeLine(int sourceCodeLine) {
        this.sourceCodeLine = sourceCodeLine;
    }

    public int getSourceCodeLine() {
        return sourceCodeLine;
    }

    public void addUrlModification(String endpoint, String targetController) {
        urlsModifications.put(endpoint, targetController);
    }

    public Map<String, String> getUrlsModifications() {
        return urlsModifications;
    }

    public void setIndentationLevel(int indentationLevel) {
        this.indentationLevel = indentationLevel;
    }

    public int getIndentationLevel() {
        return indentationLevel;
    }

    public Map<String, String> getImports() {
        return imports;
    }

    public void addImport(String importedItem, String alias) {
        imports.put(alias, importedItem);
    }

    public String resolveImportedAlias(String alias) {
        return imports.get(alias);
    }

    public void setParentScope(AbstractPythonScope parentModule) {
        this.parentScope = parentModule;
        if (!parentModule.childScopes.contains(this)) {
            parentModule.childScopes.add(this);
        }
    }

    public AbstractPythonScope getParentScope() {
        return parentScope;
    }

    public void addChildScope(AbstractPythonScope newChild) {
        newChild.setParentScope(this);
    }

    public Collection<AbstractPythonScope> getChildScopes() {
        return childScopes;
    }

    public <T extends AbstractPythonScope> Collection<T> getChildScopes(@Nonnull Class<T> type) {
        List<T> result = new LinkedList<T>();
        for (AbstractPythonScope scope : childScopes) {
            if (type.isAssignableFrom(scope.getClass())) {
                result.add((T)scope);
            }
        }
        return result;
    }


    public String getFullName() {
        List<AbstractPythonScope> parentChain = list();
        AbstractPythonScope currentScope = this;
        while (currentScope != null) {
            parentChain.add(currentScope);
            currentScope = currentScope.getParentScope();
        }
        Collections.reverse(parentChain);
        StringBuilder fullName = new StringBuilder();
        for (AbstractPythonScope scope : parentChain) {
            if (fullName.length() > 0) {
                fullName.append('.');
            }
            fullName.append(scope.getName());
        }
        return fullName.toString();
    }

    public <T extends AbstractPythonScope> T findParent(Class<T> type) {
        AbstractPythonScope current = this.getParentScope();
        while (current != null) {
            if (type.isAssignableFrom(current.getClass())) {
                return (T)current;
            }
            current = current.getParentScope();
        }
        return null;
    }

    public void accept(PythonVisitor visitor) {
        Collection<PythonClass> classes = getChildScopes(PythonClass.class);
        Collection<PythonFunction> functions = getChildScopes(PythonFunction.class);
        Collection<PythonModule> modules = getChildScopes(PythonModule.class);
        Collection<PythonPublicVariable> variables = getChildScopes(PythonPublicVariable.class);

        for (PythonClass pyClass : classes) {
            visitor.visitClass(pyClass);
            pyClass.accept(visitor);
        }

        for (PythonFunction pyFunction : functions) {
            visitor.visitFunction(pyFunction);
            pyFunction.accept(visitor);
        }

        for (PythonModule pyModule : modules) {
            visitor.visitModule(pyModule);
            pyModule.accept(visitor);
        }

        for (PythonPublicVariable pyVariable : variables) {
            visitor.visitPublicVariable(pyVariable);
            pyVariable.accept(visitor);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + getFullName() + " - " + getSourceCodePath();
    }
}
