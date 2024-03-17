/*
 * Copyright (c) 2023 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package com.didalgo.intellij.chatgpt.ui;

import com.didalgo.intellij.chatgpt.SystemMessageHolder;
import com.didalgo.intellij.chatgpt.chat.ChatLink;
import com.didalgo.intellij.chatgpt.text.TextFragment;
import com.didalgo.intellij.chatgpt.util.ScrollingTools;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.ui.Gray;
import com.intellij.ui.HideableTitledPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.didalgo.intellij.chatgpt.settings.OpenAISettingsState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import static com.didalgo.intellij.chatgpt.settings.OpenAISettingsState.BASE_PROMPT;

/**
 * toolwindow 中展示消息的 panel，也就是除了最下方输入框和提交按钮，就是这部分内容了
 */
public class MessageGroupComponent extends JBPanel<MessageGroupComponent> implements NullableComponent, SystemMessageHolder {
    // 消息实际都放到这个垂直面板中
    private final JPanel myList = new JPanel(new VerticalLayout(0));
    // 展示消息的滚动面板，纵向根据需要加滚动条，横向不显示滚动条
    private final JBScrollPane myScrollPane = new JBScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    // 记录了滚动位置
    private int myScrollValue = 0;
    // 上方 System Role 提示词输入框
    private JBTextField systemRole;
    private final Project project;
    private final ChatLink chatLink;

    public MessageGroupComponent(ChatLink chatLink, @NotNull Project project) {
        this.chatLink = chatLink;
        this.project = project;

        // 设置一些样式
        setBorder(JBUI.Borders.empty());
        setLayout(new BorderLayout());
        setBackground(UIUtil.getListBackground());

        myScrollPane.getVerticalScrollBar().putClientProperty(JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, Boolean.TRUE);
        // 给 ScrollPanel 设置监听器，当滚动条在底部的时候，页面变化一直滚动到底部，如果滑动滚动条到上方，则停止滚动到底部
        ScrollingTools.installAutoScrollToBottom(myScrollPane);

        // 这个代码块定义了 toolwindow 最上方的 System Role 区域组件
        {
            // 这个 panel 用于放 title 和下方这个 panel
            JPanel panel = new NonOpaquePanel(new GridLayout(0,1));

            // 这个 panel 用于放输入框以及后面的保存、撤回按钮
            JPanel rolePanel = new NonOpaquePanel(new BorderLayout());

            // system role 输入框
            systemRole = new JBTextField();
            OpenAISettingsState instance = OpenAISettingsState.getInstance();
            systemRole.setText(instance.gpt35RoleText);
            systemRole.setEnabled(false);
            // 当鼠标移入时可以修改，鼠标移出后不可修改
            systemRole.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    systemRole.setEnabled(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    systemRole.setEnabled(false);
                }
            });
            rolePanel.add(systemRole, BorderLayout.CENTER);

            // system role 右边的保存、撤回按钮
            DefaultActionGroup toolbarActions = new DefaultActionGroup();
            toolbarActions.add(new AnAction(AllIcons.Actions.MenuSaveall) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    instance.gpt35RoleText = systemRole.getText().isEmpty() ? BASE_PROMPT : systemRole.getText();
                }
            });
            toolbarActions.add(new AnAction(AllIcons.Actions.Rollback) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    systemRole.setText(BASE_PROMPT);
                    instance.setGpt35RoleText(BASE_PROMPT);
                }
            });
            ActionToolbarImpl actonPanel = new ActionToolbarImpl("System Role Toolbar",toolbarActions,true);
            actonPanel.setTargetComponent(this);
            rolePanel.add(actonPanel,BorderLayout.EAST);
            panel.add(rolePanel);
            panel.setBorder(JBUI.Borders.empty(0,8,10,0));

            // 可折叠的 title 组件
            HideableTitledPanel cPanel = new HideableTitledPanel("System role: you can guide your assistant and define its behavior.", false);
            cPanel.setContentComponent(panel);
            cPanel.setOn(false);
            cPanel.setBorder(JBUI.Borders.empty(0,8,10,0));
            add(cPanel, BorderLayout.NORTH);
        }

        // 这个 mainPanel 定义了中间对话消息的内容
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setOpaque(false); // 设置透明
        mainPanel.setBorder(JBUI.Borders.emptyLeft(0));
        add(mainPanel, BorderLayout.CENTER);

        //这个代码块定义了 System Role 下方的 Conversion 和 New Chat
        {
            JBLabel myTitle = new JBLabel("Conversation");
            myTitle.setForeground(JBColor.namedColor("Label.infoForeground", new JBColor(Gray.x80, Gray.x8C)));
            myTitle.setFont(JBFont.label());

            LinkLabel<String> newChat = new LinkLabel<>("New chat", null);
            newChat.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    myList.removeAll();
                    addAssistantTipsIfEnabled(false);
                    myList.updateUI();
                    chatLink.getConversationContext().clear();
                }
            });

            newChat.setFont(JBFont.label());
            newChat.setBorder(JBUI.Borders.emptyRight(20));

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(JBUI.Borders.empty(0, 10, 10, 0));
            panel.add(myTitle, BorderLayout.WEST);
            panel.add(newChat, BorderLayout.EAST);
            mainPanel.add(panel, BorderLayout.NORTH);
        }

        // 将消息列表组件添加到 mainPanel
        myList.setOpaque(true);
        myList.setBackground(UIUtil.getListBackground());
        myList.setBorder(JBUI.Borders.emptyRight(0));

        myScrollPane.setBorder(JBUI.Borders.empty());
        mainPanel.add(myScrollPane);
        myScrollPane.getVerticalScrollBar().setAutoscrolls(true);
        // 滚动后记录滚动的位置
        myScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            myScrollValue = e.getValue();
        });

        // 第一次进入时在消息框中展示提示
        addAssistantTipsIfEnabled(true);
    }

    /**
     * 在消息中间添加分割线
     * @param comp
     */
    public void addSeparator(JComponent comp) {
        SwingUtilities.invokeLater(() -> {
            JSeparator separator = new JSeparator();
            separator.setForeground(JBColor.border());
            comp.add(separator);
            updateLayout();
            invalidate();
            validate();
            repaint();
        });
    }

    protected void addAssistantTipsIfEnabled(boolean firstUse) {
        addSeparator(myList);

        var introEnabled = OpenAISettingsState.getInstance().getEnableInitialMessage();
        if (!firstUse && introEnabled == null)
            OpenAISettingsState.getInstance().setEnableInitialMessage(introEnabled = false);
        if (!Boolean.FALSE.equals(introEnabled))
            myList.add(createAssistantTips());
    }

    protected MessageComponent createAssistantTips() {
        var modelType = chatLink.getConversationContext().getModelType();
        return new MessageComponent(TextFragment.of("""
                Hi, I'm your AI-powered annoying pair programmer. How can I assist you today?
                
                Here are some suggestions to get you started:
                [✦ Explain the selected code](assistant://?prompt=Explain+the+selected+code)
                [✦ Convert this Oracle SQL to PostgreSQL](assistant://?prompt=Convert+this+Oracle+SQL+to+PostgreSQL)
                [✦ What for can I use atomics in Java?](assistant://?prompt=What+for+can+I+use+atomics+in+Java%3F)
                [✦ Explain the LazyHolder pattern in Java](assistant://?prompt=Explain+the+LazyHolder+pattern+in+Java)
                [✦ Suggest Java library's method for doing OCR](assistant://?prompt=Suggest+Java+library%27s+method+for+doing+OCR)
                """), modelType);
    }

    public void add(MessageComponent messageComponent) {
        SwingUtilities.invokeLater(() -> {
            myList.add(messageComponent);
            updateLayout();
            scrollToBottom();
            invalidate();
            validate();
            repaint();
        });
    }

    public void scrollToBottom() {
        ScrollingTools.scrollToBottom(myScrollPane);
    }

    public void updateLayout() {
        LayoutManager layout = myList.getLayout();
        int componentCount = myList.getComponentCount();
        for (int i = 0; i < componentCount; i++) {
            layout.removeLayoutComponent(myList.getComponent(i));
            layout.addLayoutComponent(null, myList.getComponent(i));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (myScrollValue > 0) {
            g.setColor(JBColor.border());
            int y = myScrollPane.getY() - 1;
            g.drawLine(0, y, getWidth(), y);
        }
    }

    @Override
    public boolean isVisible() {
        if (super.isVisible()) {
            int count = myList.getComponentCount();
            for (int i = 0 ; i < count ; i++) {
                if (myList.getComponent(i).isVisible()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isNull() {
        return !isVisible();
    }

    @Override
    public String getSystemMessage() {
        return systemRole.getText();
    }
}
