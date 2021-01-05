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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

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
        e.getPresentation().setEnabled(psiType != null);
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

        String className = MyPojoToJsonCore.getClassName(psiType);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Converting " + className + " to JSON...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // 10% done
                indicator.setFraction(0.1);
                indicator.setText("90% to finish");
                try {
                    Object result = ApplicationManager.getApplication().runReadAction(new Computable<Object>() {
                        @Override
                        public Object compute() {
                            return MyPojoToJsonCore.resolveType(psiType, new ProcessingInfo().setProject(project).setProgressIndicator(indicator));
                        }
                    });
                    String json = GSON.toJson(result);
                    try {
                        json = MyPojoToJsonCore.myFormat(json);
                    } catch (IOException ex) {
                        throw new MyPluginException("Error", ex);
                    }
                    StringSelection selection = new StringSelection(json);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);
                } catch (ProcessCanceledException e) {
                    return;
                } finally {
                    // Finished
                    indicator.setFraction(1.0);
                    indicator.setText("finished");
                    indicator.cancel();
                }

                String message = "Convert " + className + " to JSON success, copied to clipboard.";
                Notification success = notifyGroup.createNotification(message, NotificationType.INFORMATION);
                Bus.notify(success, project);
            }
        });

//        try {
//
//        } catch (MyPluginException exception) {
//            Bus.notify(notifyGroup.createNotification(exception.getMessage(), NotificationType.WARNING), project);
//        } catch (Exception var14) {
//            Bus.notify(notifyGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR), project);
//        }
    }

}
