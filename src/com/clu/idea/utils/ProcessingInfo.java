package com.clu.idea.utils;

import com.intellij.psi.PsiType;

import java.util.List;
import java.util.Stack;

public class ProcessingInfo {

    private int level;

    private Stack<PsiType> types = new Stack<>();

    public int increase() {
        return ++level;
    }

    public int decrease() {
        return --level;
    }

    public List<PsiType> start(PsiType psiType) {
        this.types.push(psiType);
        return this.types;
    }

    public List<PsiType> finish() {
        this.types.pop();
        return this.types;
    }

    public boolean isProcessing(PsiType psiType) {
        int count = 0;
        for (PsiType type : this.types) {
            if (type != null && type.equals(psiType)) {
                count++;
            }
        }
        // 保留1次递归信息
        return count > 1;
    }

    public int getLevel() {
        return level;
    }
}
