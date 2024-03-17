package com.didalgo.intellij.chatgpt.text;

import com.didalgo.intellij.chatgpt.ChatGptBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class CodeFragmentFactory {

    // 从 editor 获取选中的内容
    public static Optional<CodeFragment> createFromSelection(Editor editor) {
        var selectionModel = editor.getSelectionModel();
        var text = "";
        if (selectionModel.hasSelection() && (text = selectionModel.getSelectedText()) != null && !text.isBlank()) {
            // 如果有，则封装成 CodeFragment
            return Optional.of(create(editor, text));
        }
        return Optional.empty();
    }

    public static CodeFragment create(Editor editor) {
        var selectionModel = editor.getSelectionModel();
        var text = "";
        if (selectionModel.hasSelection() && (text = selectionModel.getSelectedText()) != null && !text.isBlank()) {
            return create(editor, text);
        }

        // If no selection, return CodeFragment from entire editor content
        return create(editor, editor.getDocument().getText());
    }

    // 封装成 CodeFragment，相关属性有文件扩展、文件url，选中内容
    public static CodeFragment create(Editor editor, String textContent) {
        @SuppressWarnings("RedundantCast")
        var file = ((EditorEx) editor).getVirtualFile();
        var fileExtension = (file == null)? "" : StringUtils.defaultIfEmpty(file.getExtension(), "");
        var fileUrl = (file == null)? "" : file.getUrl();
        return CodeFragment.of(textContent, fileExtension, ChatGptBundle.message("code.fragment.title", fileUrl));
    }
}
