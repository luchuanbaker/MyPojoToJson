package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications.Bus;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;

public class MyPojoToJsonAction extends AnAction {

    // private static final GsonBuilder gsonBuilder = (new GsonBuilder()).setPrettyPrinting();
    private static final Gson GSON = (new GsonBuilder()).serializeNulls().setPrettyPrinting().create();

    private static final NotificationGroup notifyGroup = new NotificationGroup("myPojoToJson.NotificationGroup", NotificationDisplayType.BALLOON, true);

    @Override
    public void update(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        PsiType psiType = MyPojoToJsonCore.checkAndGetPsiType(dataContext);
        if (psiType == null) {
            e.getPresentation().setEnabled(false);
        }
        e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        DataContext dataContext = e.getDataContext();

        PsiClassType psiType = MyPojoToJsonCore.checkAndGetPsiType(dataContext);
        if (psiType == null) {
            return;
        }

        Object result = MyPojoToJsonCore.resolveType(psiType, new ProcessingInfo());
        String json = GSON.toJson(result);
        try {
            json = MyPojoToJsonCore.myFormat(json);
        } catch (IOException ex) {
            throw new MyPluginException("Error", ex);
        }
        StringSelection selection = new StringSelection(json);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
        String message = "Convert " + MyPojoToJsonCore.getClassName(psiType) + " to JSON success, copied to clipboard.";
        Notification success = notifyGroup.createNotification(message, NotificationType.INFORMATION);
        Bus.notify(success, project);

//        try {
//
//        } catch (MyPluginException exception) {
//            Bus.notify(notifyGroup.createNotification(exception.getMessage(), NotificationType.WARNING), project);
//        } catch (Exception var14) {
//            Bus.notify(notifyGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR), project);
//        }
    }

}
