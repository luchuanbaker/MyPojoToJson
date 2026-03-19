package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;

import static com.clu.idea.utils.MyPojoToJsonCore.GSON;

public class MyPojoToJsonAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(MyPojoToJsonAction.class);
    private static final String NOTIFICATION_GROUP_ID = "MyPojoToJson";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // Keep action visible in Java files; resolve actual type in actionPerformed.
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiClassType psiType = ReadAction.compute(() -> resolvePsiType(e));
        if (psiType == null) {
            LOG.warn("MyPojoToJson: unable to resolve class type from current context.");
            notifyUser(project, NotificationType.WARNING, "Cannot resolve Java class type at current cursor.");
            return;
        }

        String className = MyPojoToJsonCore.getClassName(psiType);
        LOG.info("MyPojoToJson: start converting class " + className);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Converting " + className + " to JSON...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setFraction(0.1);
                indicator.setText("90% to finish");
                ProcessingInfo processingInfo = new ProcessingInfo().setProject(project).setProgressIndicator(indicator);
                try {
                    Object result = ReadAction.compute(() -> MyPojoToJsonCore.resolveType(psiType, processingInfo));
                    processingInfo.setResult(result);
                } catch (ProcessCanceledException ex) {
                    LOG.warn("MyPojoToJson: conversion canceled for class " + className, ex);
                    notifyUser(project, NotificationType.WARNING, "Convert canceled: " + className);
                    return;
                } catch (Throwable ex) {
                    LOG.warn("MyPojoToJson: conversion failed for class " + className, ex);
                    notifyUser(project, NotificationType.ERROR, "Convert failed: " + className + ", check idea.log for details.");
                    return;
                } finally {
                    indicator.setFraction(1.0);
                    indicator.setText("finished");
                }

                Object result = processingInfo.getResult();
                if (result == null) {
                    LOG.warn("MyPojoToJson: conversion result is null for class " + className);
                    notifyUser(project, NotificationType.WARNING, "Convert failed: empty result for " + className);
                    return;
                }

                String json = GSON.toJson(result);
                try {
                    json = MyPojoToJsonCore.myFormat(json);
                } catch (IOException ex) {
                    LOG.warn("MyPojoToJson: json format failed for class " + className, ex);
                    notifyUser(project, NotificationType.ERROR, "Convert failed during formatting, check idea.log.");
                    throw new MyPluginException("Error", ex);
                }

                CopyPasteManager.getInstance().setContents(new StringSelection(json));
                LOG.info("MyPojoToJson: conversion success for class " + className + ", json length=" + json.length());
                notifyUser(project, NotificationType.INFORMATION,
                    "Convert " + className + " to JSON success, copied to clipboard.");
            }
        });
    }

    private static PsiClassType resolvePsiType(@NotNull AnActionEvent e) {
        PsiClassType psiType = MyPojoToJsonCore.checkAndGetPsiType(e.getDataContext());
        if (psiType != null) {
            return psiType;
        }

        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement == null) {
            PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
            if (psiFile instanceof PsiJavaFile) {
                PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
                if (classes.length > 0) {
                    return PsiTypesUtil.getClassType(classes[0]);
                }
            }
            return null;
        }

        if (psiElement instanceof PsiTypeElement) {
            PsiType selectedType = ((PsiTypeElement) psiElement).getType();
            if (selectedType instanceof PsiClassType) {
                return (PsiClassType) selectedType;
            }
        }

        PsiClass psiClass = psiElement instanceof PsiClass
            ? (PsiClass) psiElement
            : PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
        if (psiClass != null) {
            return PsiTypesUtil.getClassType(psiClass);
        }
        return null;
    }

    private static void notifyUser(Project project, NotificationType type, String message) {
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
        group.createNotification(message, type).notify(project);
    }
}
