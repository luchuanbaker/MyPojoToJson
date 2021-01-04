package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;

import java.util.Optional;
import java.util.Stack;

public class ProcessingInfo {

    private int level;

    private Project project;

    private Stack<Object> listingFieldsTypes = new Stack<>();

    public int increase() {
        return ++level;
    }

    public int decrease() {
        return --level;
    }

    public Project getProject() {
        return project;
    }

    public ProcessingInfo setProject(Project project) {
        this.project = project;
        return this;
    }

    public void startListFields(PsiType psiType) {
        // 去除泛型信息，递归校验认为：泛型信息不一样，但是类一样，也算是递归
        Object stackItem = removeGenericInfo(psiType);
        this.listingFieldsTypes.push(stackItem);
    }

    private Object removeGenericInfo(PsiType psiType) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
        return Optional.<Object>ofNullable(psiClass).orElse(psiType);
    }

    public void finishListFields() {
        this.listingFieldsTypes.pop();
    }

    private int getCount(Stack<Object> stack, Object psiType) {
        int count = 0;
        for (Object type : stack) {
            if (type != null && type.equals(psiType)) {
                count++;
            }
        }
        return count;
    }

    public boolean isListingFields(PsiType psiType) {
        // 保留1次递归信息
        return getCount(this.listingFieldsTypes, removeGenericInfo(psiType)) > 0;
    }

    public void checkOverflow() {
        if (level > 50) {
            throw new MyPluginException(new MyPluginException("This class reference level exceeds maximum limit or has nested references!"));
        }
    }
}
