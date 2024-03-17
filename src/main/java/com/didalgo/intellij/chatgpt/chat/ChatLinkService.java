/*
 * Copyright (c) 2023 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package com.didalgo.intellij.chatgpt.chat;

import com.didalgo.intellij.chatgpt.core.TextSubstitutor;
import com.didalgo.intellij.chatgpt.text.TextContent;
import com.didalgo.intellij.chatgpt.ui.context.stack.DefaultInputContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.theokanning.openai.completion.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChatLinkService extends AbstractChatLink {

    private final Project project;
    // 保存了鼠标右键，点击 add to context 的内容
    private final InputContext inputContext;
    // 实际发送请求的处理器
    private final ConversationHandler conversationHandler;
    private final ChatLinkState conversationContext;

    public ChatLinkService(Project project, ConversationHandler engine, ConfigurationPage configuration) {
        this.project = project;
        this.conversationHandler = engine;
        this.conversationContext = new ChatLinkState(configuration);
        this.conversationContext.setTextSubstitutor(project.getService(TextSubstitutor.class));

        // 保存了鼠标右键，点击 add to context 的内容
        this.inputContext = new DefaultInputContext();
    }

    @Override
    public Project getProject() {
        return project;
    }

    @Override
    public InputContext getInputContext() {
        return inputContext;
    }

    @Override
    public ConversationContext getConversationContext() {
        return conversationContext;
    }

    /**
     * 组装消息
     * @param prompt 输入框中输入的内容
     * @param textContents 代码编辑器中选中的代码
     */
    @Override
    public void pushMessage(String prompt, List<? extends TextContent> textContents) {
        pushMessage(prompt, textContents, getInputContext());
    }

    /**
     * 组装消息
     * @param prompt 输入框中输入的内容
     * @param textContents 代码编辑器中选中的代码
     * @param inputContext 手动 add to context 的内容
     */
    public void pushMessage(String prompt, List<? extends TextContent> textContents, InputContext inputContext) {
        // 将三种消息进行组合，组合成 ChatMessage
        ChatMessageComposer composer = ApplicationManager.getApplication().getService(ChatMessageComposer.class);
        List<? extends TextContent> mergedCtx = mergeContext(textContents, inputContext);
        ChatMessage message = composer.compose(conversationContext, prompt, mergedCtx);
        if (message.getContent().isEmpty()) {
            return;
        }

        inputContext.clear();

        // 获取一个代理监听器，可以调用所有注册监听器的监听方法
        ChatMessageListener listener = this.chatMessageListeners.fire();
        // message 封装成一个 event
        ChatMessageEvent.Starting event = ChatMessageEvent.starting(this, message);
        try {
            // 调用监听器的方法，目前只有 MainPanel 实现了这个监听器
            listener.exchangeStarting(event);
            // 请求 gpt 接口
            conversationHandler.push(conversationContext, event, listener);
        } catch (ChatExchangeAbortException ex) {
            // 发送中断处理，调用监听器的 cancel 方法，实际上什么都没做
            listener.exchangeCancelled(event.cancelled());
            // 设置上一次发送的代码片段为空
            getConversationContext().setLastPostedCodeFragments(List.of());
        } catch (Throwable x) {
            // 显示错误信息
            listener.exchangeFailed(event.failed(x));
            getConversationContext().setLastPostedCodeFragments(List.of());
        }
    }

    private static List<? extends TextContent> mergeContext(List<? extends TextContent> textContents, InputContext inputContext) {
        if (inputContext.getEntries().isEmpty()) {
            return textContents;
        }

        List<TextContent> list = new ArrayList<>();
        Optional<TextContent> code;
        for (var contextEntry : inputContext.getEntries())
            if ((code = contextEntry.getTextContent()).isPresent())
                list.add(code.get());

        for (var codeFragment : textContents)
            if (!list.contains(codeFragment))
                list.add(codeFragment);

        return list;
    }
}
