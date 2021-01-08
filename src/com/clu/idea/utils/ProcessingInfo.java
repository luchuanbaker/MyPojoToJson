package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class ProcessingInfo {

    private AtomicReference<Object> result = new AtomicReference<>();

    private int level;

    private Project project;

    private ProgressIndicator progressIndicator;

    private Stack<PsiType> processingTypes = new Stack<>();

    public void startProcessType(PsiType psiType) {
        this.processingTypes.push(psiType);
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

    enum CheckProcessingType {
        NO,
        PROCESSING,
        MAX_DEPTH
    }

    public CheckProcessingType checkProcessingType(PsiType psiType) {
        if (this.processingTypes.size() > 10) {
            return CheckProcessingType.MAX_DEPTH;
        }
        if (this.processingTypes.contains(psiType)) {
            return CheckProcessingType.PROCESSING;
        }
        return CheckProcessingType.NO;
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

    // getters and setters

    public Object getResult() {
        return this.result.get();
    }

    public ProcessingInfo setResultIfAbsent(Object result) {
        this.result.compareAndSet(null, result);
        return this;
    }

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

}
