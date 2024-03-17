/*
 * Copyright (c) 2023 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package com.didalgo.intellij.chatgpt.ui;

import com.didalgo.gpt3.ModelType;
import com.didalgo.intellij.chatgpt.chat.*;
import com.didalgo.intellij.chatgpt.core.ChatCompletionParser;
import com.didalgo.intellij.chatgpt.text.TextContent;
import com.didalgo.intellij.chatgpt.ui.context.stack.TextInputContextEntry;
import com.didalgo.intellij.chatgpt.ui.context.stack.ListStack;
import com.didalgo.intellij.chatgpt.ui.context.stack.ListStackFactory;
import com.didalgo.intellij.chatgpt.settings.OpenAISettingsPanel;
import com.didalgo.intellij.chatgpt.settings.OpenAISettingsState;
import com.didalgo.intellij.chatgpt.text.TextFragment;
import com.didalgo.intellij.chatgpt.ui.action.tool.SettingsAction;
import com.didalgo.intellij.chatgpt.ui.listener.SubmitListener;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.notification.BrowseNotificationAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.OnePixelSplitter;
import com.didalgo.intellij.chatgpt.ChatGptBundle;
import com.intellij.util.ui.JBUI;
import com.theokanning.openai.completion.chat.ChatMessage;
import io.reactivex.disposables.Disposable;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Subscription;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

import static java.awt.event.InputEvent.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class MainPanel implements ChatMessageListener {

    // 最下方可变大的输入框
    private final ExpandableTextFieldExt searchTextField;
    // 提交按钮
    private final JButton button;
    // 停止按钮
    private final JButton stopGenerating;
    // 上面对话框面板
    private final MessageGroupComponent contentPanel;
    private final JProgressBar progressBar;
    // 分割线，
    private final OnePixelSplitter splitter;
    private final Project myProject;
    // 提交部分的面板
    private JPanel actionPanel;
    // 保存当前正在进行中的请求
    private volatile Object requestHolder;
    // 请求 gpt 处理器
    private final MainConversationHandler conversationHandler;
    private ListStack contextStack;
    // 组装消息的东西
    private final ChatLink chatLink;

    public static final KeyStroke SUBMIT_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, CTRL_DOWN_MASK);

    public MainPanel(@NotNull Project project, ConfigurationPage configuration) {
        myProject = project;
        // 用于请求gpt服务获取响应的处理器
        conversationHandler = new MainConversationHandler(this);
        // 用于组装要发送gpt的message（因为 message 来自于很多地方，比如用户输入、鼠标选择等等），然后调用 MainConversationHandler 发送请求
        chatLink = new ChatLinkService(project, conversationHandler, configuration.withSystemPrompt(() -> getContentPanel().getSystemMessage()));
        // MainPanel 实现了 ChatMessageListener 接口，所以它本身会处理 gpt 响应的数据，具体看下方的 @Override 的方法
        chatLink.addChatMessageListener(this);
        // 这个 ContextAwareSnippetizer 用于保存当前编辑器选中的代码片段
        ContextAwareSnippetizer snippetizer = ApplicationManager.getApplication().getService(ContextAwareSnippetizer.class);
        // toolWindow 中点击 send 触发的动作
        SubmitListener submitAction = new SubmitListener(chatLink, this::getSearchText, snippetizer);

        // 一个像素的分割线，分割内容区域和下方的提交区域
        splitter = new OnePixelSplitter(true,.98f);
        splitter.setDividerWidth(1);
        // 放入一个 key / value 数据到 splitter 中，具体干什么用暂时不知道
        splitter.putClientProperty(HyperlinkListener.class, submitAction);

        // 可展开的输入框，就是 toolWindow 下方那个输入框，可以点击扩展图标变大
        searchTextField = new ExpandableTextFieldExt(project);
        // 拿到 textField 的 document 对象用于控制输入的文本
        var searchTextDocument = (AbstractDocument) searchTextField.getDocument();
        // 加入了一个过滤器来控制换行的行为
        searchTextDocument.setDocumentFilter(new NewlineFilter());
        searchTextDocument.putProperty("filterNewlines", Boolean.FALSE);
        searchTextDocument.addDocumentListener(new ExpandableTextFieldExt.ExpandOnMultiLinePaste(searchTextField));
        searchTextField.setMonospaced(false);
        // 设置监听器，提交时触发
        searchTextField.addActionListener(submitAction);
        // 注册提交的快捷键
        searchTextField.registerKeyboardAction(submitAction, SUBMIT_KEYSTROKE, JComponent.WHEN_FOCUSED);
        // 设置 placeholder
        searchTextField.getEmptyText().setText("Type a prompt here");
        button = new JButton(submitAction);
        // 设置按钮需要设置黑暗风格
        button.setUI(new DarculaButtonUI());

        // 停止响应按钮
        stopGenerating = new JButton("Stop", AllIcons.Actions.Suspend);
        stopGenerating.addActionListener(e -> {
            aroundRequest(false);
            if (requestHolder instanceof Disposable disposable) {
                disposable.dispose();
            } else if (requestHolder instanceof Subscription subscription) {
                subscription.cancel();
            }
        });
        stopGenerating.setUI(new DarculaButtonUI());

        // 最下方提交框和按钮的面板
        actionPanel = new JPanel(new BorderLayout());
        actionPanel.add(createContextSnippetsComponent(), BorderLayout.NORTH);  // 选择文本，右键 add：ChatGpt: add Context 后出现的文件样式
        actionPanel.add(searchTextField, BorderLayout.CENTER);
        actionPanel.add(button, BorderLayout.EAST);

        // 上方的消息面板
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        contentPanel = new MessageGroupComponent(chatLink, project);
        contentPanel.add(progressBar, BorderLayout.SOUTH);

        // 设置分割线
        splitter.setFirstComponent(contentPanel);
        splitter.setSecondComponent(actionPanel);
    }

    // 创建选中文件列表组件 ChatGpt: add Context 触发
    private JComponent createContextSnippetsComponent() {
        // Creating an instance of ListPopupShower for testing
        ListStackFactory listStackFactory = new ListStackFactory();

        // Showing the list popup
        InputContext chatInputContext = getChatLink().getInputContext();
        contextStack = listStackFactory.showListPopup(actionPanel, getProject(), chatInputContext, this::computeTokenCount);
        JList list = contextStack.getList();
        list.setBackground(actionPanel.getBackground());
        list.setBorder(JBUI.Borders.emptyTop(3));
        list.setFocusable(false);
        list.getModel().addListDataListener(new ContextStackHandler());
        list.setVisible(false);
        contextStack.beforeShow();

        chatInputContext.addListener(event -> {
            contextStack.getListModel().syncModel();
            searchTextField.requestFocusInWindow();
            actionPanel.revalidate();
        });

        return list;
    }

    private int computeTokenCount(TextInputContextEntry info) {
        var tokenCount = 0;
        if (info.getTextContent().isPresent())
            tokenCount = getModelType().getTokenizer().encode(TextContent.toString(info.getTextContent().get())).size();
        info.setTokenCount(tokenCount);

        SwingUtilities.invokeLater(() -> {
            contextStack.getListModel().syncModel();

            actionPanel.revalidate();
        });
        return tokenCount;
    }

    private class ContextStackHandler implements ListDataListener {

        protected void onContentsChange() {
            var hasContext = !getChatLink().getInputContext().isEmpty();
            if (hasContext != contextStack.getList().isVisible())
                contextStack.getList().setVisible(hasContext);
        }

        @Override
        public void intervalAdded(ListDataEvent e) {
            onContentsChange();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            onContentsChange();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            onContentsChange();
        }
    }

    public final ChatLink getChatLink() {
        return chatLink;
    }

    public ModelType getModelType() {
        return getChatLink().getConversationContext().getModelType();
    }

    // 在请求 gpt 前的监听器回调
    @Override
    public void exchangeStarting(ChatMessageEvent.Starting event) throws ChatExchangeAbortException {
        // 检查 api key 是否设置
        if (!presetCheck()) {
            throw new ChatExchangeAbortException("Preset check failed");
        }

        // 把问题放入对话列表，加入一个 Thinking... 的对话
        TextFragment userMessage = TextFragment.of(event.getUserMessage().getContent());
        question = new MessageComponent(userMessage, null);
        answer = new MessageComponent(TextFragment.of("Thinking..."), getModelType());
        SwingUtilities.invokeLater(() -> {
            setSearchText("");
            aroundRequest(true);

            MessageGroupComponent contentPanel = getContentPanel();
            contentPanel.add(question);
            contentPanel.add(answer);
        });
    }

    private volatile MessageComponent question, answer;

    @Override
    public void exchangeStarted(ChatMessageEvent.Started event) {
        setRequestHolder(event.getSubscription());

        SwingUtilities.invokeLater(contentPanel::updateLayout);
    }

    // 检查 api 是否设置
    protected boolean presetCheck() {
        OpenAISettingsState instance = OpenAISettingsState.getInstance();
        String page = getChatLink().getConversationContext().getModelPage();
        if (StringUtils.isEmpty(instance.getConfigurationPage(page).getApiKey())) {
            Notification notification = new Notification(ChatGptBundle.message("group.id"),
                    ChatGptBundle.message("notify.config.title"),
                    ChatGptBundle.message("notify.config.text"),
                    NotificationType.ERROR);
            notification.addAction(new SettingsAction(ChatGptBundle.message("notify.config.action.config"), OpenAISettingsPanel.getTargetPanelClassForPage(page)));
            notification.addAction(new BrowseNotificationAction(ChatGptBundle.message("notify.config.action.browse"), ChatGptBundle.message("notify.config.action.browse.url")));
            Notifications.Bus.notify(notification);
            return false;
        }
        return true;
    }

    // 流数据正在源源不断过来，每部分数据都会调用这个
    @Override
    public void responseArriving(ChatMessageEvent.ResponseArriving event) {
        setContent(event.getPartialResponseChoices());
    }

    // 流数据完成后回调
    @Override
    public void responseArrived(ChatMessageEvent.ResponseArrived event) {
        setContent(event.getResponseChoices());
        SwingUtilities.invokeLater(() -> {
            aroundRequest(false);
        });
    }

    public void setContent(List<ChatMessage> content) {
        TextFragment parseResult = ChatCompletionParser.parseGPT35TurboWithStream(content);
        answer.setContent(parseResult);
    }

    @Override
    public void exchangeFailed(ChatMessageEvent.Failed event) {
        // 显示错误信息
        if (answer != null) {
            answer.setErrorContent(getErrorMessage(event.getCause()));
        }
        aroundRequest(false);
    }

    private String getErrorMessage(Throwable cause) {
        if (cause == null)
            return "";
        return (isEmpty(cause.getMessage()) ? "" : cause.getMessage() + "; ")
                + getErrorMessage(cause.getCause());
    }

    @Override
    public void exchangeCancelled(ChatMessageEvent.Cancelled event) {

    }

    public void responseArrivalFailed(ChatMessageEvent.Failed event) {
        if (event.getCause() instanceof StreamResetException) {
            answer.setErrorContent("*Request failure*, cause: " + event.getCause().getMessage());
            aroundRequest(false);
            event.getCause().printStackTrace();
            return;
        }
        answer.setErrorContent("*Response failure*, cause: " + event.getCause().getMessage() + ", please try again.\n\n Tips: if proxy is enabled, please check if the proxy server is working.");
        SwingUtilities.invokeLater(() -> {
            aroundRequest(false);
            contentPanel.scrollToBottom();
        });
    }

    public Project getProject() {
        return myProject;
    }

    public String getSearchText() {
        return searchTextField.getText();
    }

    public void setSearchText(String t) {
        searchTextField.setText(t);
    }

    public MessageGroupComponent getContentPanel() {
        return contentPanel;
    }

    public JPanel init() {
        return splitter;
    }

    public void aroundRequest(boolean status) {
        progressBar.setIndeterminate(status);
        progressBar.setVisible(status);
        button.setEnabled(!status);
        if (status) {
            actionPanel.remove(button);
            actionPanel.add(stopGenerating, BorderLayout.EAST);
        } else {
            actionPanel.remove(stopGenerating);
            actionPanel.add(button, BorderLayout.EAST);
        }
        actionPanel.revalidate();
        actionPanel.repaint();
    }

    public void setRequestHolder(Object eventSource) {
        this.requestHolder = eventSource;
    }

}
