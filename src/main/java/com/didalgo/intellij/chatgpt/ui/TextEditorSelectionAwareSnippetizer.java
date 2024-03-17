/*
 * Copyright (c) 2023 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package com.didalgo.intellij.chatgpt.ui;

import com.didalgo.intellij.chatgpt.text.CodeFragment;
import com.didalgo.intellij.chatgpt.text.CodeFragmentFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;

public class TextEditorSelectionAwareSnippetizer implements ContextAwareSnippetizer {

    // 获取到鼠标选中的代码片段
    @Override
    public List<CodeFragment> fetchSnippets(Project project) {
        List<CodeFragment> selectedFragments = new ArrayList<>();

        // 遍历每个文件编辑器
        for (FileEditor editor : FileEditorManager.getInstance(project).getSelectedEditors()) {
            if (editor instanceof TextEditor textEditor) {
                // 如果有选中，则添加到数组
                CodeFragmentFactory.createFromSelection(textEditor.getEditor()).ifPresent(selectedFragments::add);
            }
        }
        return selectedFragments;
    }
}
