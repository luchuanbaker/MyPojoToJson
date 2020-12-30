package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.intellij.psi.PsiType;

import java.util.Stack;

public class ProcessingInfo {

    private int level;

    private Stack<PsiType> listingFieldsTypes = new Stack<>();

    public int increase() {
        return ++level;
    }

    public int decrease() {
        return --level;
    }

    public void startListFields(PsiType psiType) {
        this.listingFieldsTypes.push(psiType);
    }

    public void finishListFields() {
        this.listingFieldsTypes.pop();
    }


    private int getCount(Stack<PsiType> stack, PsiType psiType) {
        int count = 0;
        for (PsiType type : stack) {
            if (type != null && type.equals(psiType)) {
                count++;
            }
        }
        return count;
    }

    public boolean isListingFields(PsiType psiType) {
        // 保留1次递归信息
        return getCount(this.listingFieldsTypes, psiType) > 1;
    }

    public void checkOverflow() {
        if (level > 50) {
            throw new MyPluginException(new MyPluginException("This class reference level exceeds maximum limit or has nested references!"));
        }
    }
}
