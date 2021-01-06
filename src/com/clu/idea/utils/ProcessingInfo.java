package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;

import java.util.Optional;
import java.util.Stack;

public class ProcessingInfo {

    private int level;

    private Project project;

    private ProgressIndicator progressIndicator;

    private Stack<Object> processingTypes = new Stack<>();

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

    public ProgressIndicator getProgressIndicator() {
        return progressIndicator;
    }

    public ProcessingInfo setProgressIndicator(ProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
        return this;
    }

    public void startProcessType(PsiType psiType) {
        // 去除泛型信息，递归校验认为：泛型信息不一样，但是类一样，也算是递归
        Object stackItem = removeGenericInfo(psiType);
        this.processingTypes.push(stackItem);
    }

    private Object removeGenericInfo(PsiType psiType) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
        return Optional.<Object>ofNullable(psiClass).orElse(psiType);
    }

    public void finishProcessType() {
        this.processingTypes.pop();
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

    public boolean isProcessingType(PsiType psiType) {
        return getCount(this.processingTypes, removeGenericInfo(psiType)) > 0;
    }

    public void checkOverflowAndCanceled() throws ProcessCanceledException {
        if (level > 50) {
            throw new MyPluginException(new MyPluginException("This class reference level exceeds maximum limit or has nested references!"));
        }
        this.progressIndicator.checkCanceled();
    }

    public void updateProgress(PsiType psiType) {
        this.progressIndicator.setFraction(Math.min(0.9, this.progressIndicator.getFraction() + 0.1));
        this.progressIndicator.setText("Process class: " + psiType.getPresentableText());
    }

}
