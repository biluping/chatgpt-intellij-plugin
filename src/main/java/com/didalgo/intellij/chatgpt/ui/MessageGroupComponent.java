/*
 * Copyright (c) 2023 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package com.didalgo.intellij.chatgpt.ui;

import com.didalgo.intellij.chatgpt.SystemMessageHolder;
import com.didalgo.intellij.chatgpt.chat.ChatLink;
import com.didalgo.intellij.chatgpt.text.TextFragment;
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

public class MessageGroupComponent extends JBPanel<MessageGroupComponent> implements NullableComponent, SystemMessageHolder {
    private final JPanel myList = new JPanel(new VerticalLayout(0));
    private final JBScrollPane myScrollPane = new JBScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private int myScrollValue = 0;

    private final MyAdjustmentListener scrollListener = new MyAdjustmentListener();
    private final MessageComponent tips =
            new MessageComponent(TextFragment.of("Go ahead, asketh me anything, I dare thee."),false);
    private JBTextField systemRole;
    private static final String systemRoleText = "You are an expert in software development.";
    private final Project project;
    private final ChatLink chatLink;

    public MessageGroupComponent(ChatLink chatLink, @NotNull Project project) {
        this.chatLink = chatLink;
        this.project = project;
        setBorder(JBUI.Borders.empty());
        setLayout(new BorderLayout());
        setBackground(UIUtil.getListBackground());

        myScrollPane.getVerticalScrollBar().putClientProperty(JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, Boolean.TRUE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(JBUI.Borders.emptyLeft(0));

        if (true) {
            JPanel panel = new NonOpaquePanel(new GridLayout(0,1));
            JPanel rolePanel = new NonOpaquePanel(new BorderLayout());
            systemRole = new JBTextField();
            OpenAISettingsState instance = OpenAISettingsState.getInstance();
            systemRole.setText(instance.gpt35RoleText);
            systemRole.setEnabled(false);
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
            DefaultActionGroup toolbarActions = new DefaultActionGroup();
            toolbarActions.add(new AnAction(AllIcons.Actions.MenuSaveall) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    instance.gpt35RoleText = systemRole.getText().isEmpty() ? systemRoleText : systemRole.getText();
                }
            });
            toolbarActions.add(new AnAction(AllIcons.Actions.Rollback) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    systemRole.setText(systemRoleText);
                    instance.gpt35RoleText = systemRoleText;
                }
            });
            ActionToolbarImpl actonPanel = new ActionToolbarImpl("System Role Toolbar",toolbarActions,true);
            actonPanel.setTargetComponent(this);
            rolePanel.add(actonPanel,BorderLayout.EAST);
            panel.add(rolePanel);
            panel.setBorder(JBUI.Borders.empty(0,8,10,0));

            HideableTitledPanel cPanel = new HideableTitledPanel("System role: you can guide your assistant and define its behavior.", false);
            cPanel.setContentComponent(panel);
            cPanel.setOn(false);
            cPanel.setBorder(JBUI.Borders.empty(0,8,10,0));
            add(cPanel, BorderLayout.NORTH);
        }

        add(mainPanel, BorderLayout.CENTER);

        JBLabel myTitle = new JBLabel("Conversation");
        myTitle.setForeground(JBColor.namedColor("Label.infoForeground", new JBColor(Gray.x80, Gray.x8C)));
        myTitle.setFont(JBFont.label());

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(0,10,10,0));

        panel.add(myTitle, BorderLayout.WEST);

        LinkLabel<String> newChat = new LinkLabel<>("New chat", null);
        newChat.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                myList.removeAll();
                myList.add(tips);
                myList.updateUI();
                chatLink.getConversationContext().clear();
            }
        });

        newChat.setFont(JBFont.label());
        newChat.setBorder(JBUI.Borders.emptyRight(20));
        panel.add(newChat, BorderLayout.EAST);
        mainPanel.add(panel, BorderLayout.NORTH);

        myList.setOpaque(true);
        myList.setBackground(UIUtil.getListBackground());
        myList.setBorder(JBUI.Borders.emptyRight(0));

        myScrollPane.setBorder(JBUI.Borders.empty());
        mainPanel.add(myScrollPane);
        myScrollPane.getVerticalScrollBar().setAutoscrolls(true);
        myScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            int value = e.getValue();
            if (myScrollValue == 0 && value > 0 || myScrollValue > 0 && value == 0) {
                myScrollValue = value;
                repaint();
            }
            else {
                myScrollValue = value;
            }
        });

        myList.add(tips);
    }

    public void add(MessageComponent messageComponent) {
        // The component should be immediately added to the container and displayed in the UI

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
        JScrollBar verticalScrollBar = myScrollPane.getVerticalScrollBar();
        verticalScrollBar.setValue(Integer.MAX_VALUE);
    }

    public void updateLayout() {
        LayoutManager layout = myList.getLayout();
        int componentCount = myList.getComponentCount();
        for (int i = 0 ; i< componentCount ; i++) {
            layout.removeLayoutComponent(myList.getComponent(i));
            layout.addLayoutComponent(null,myList.getComponent(i));
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

    static class MyAdjustmentListener implements AdjustmentListener {

        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
            JScrollBar source = (JScrollBar) e.getSource();
            if (!source.getValueIsAdjusting()) {
                source.setValue(source.getMaximum());
            }
        }
    }

    public void addScrollListener() {
        myScrollPane.getVerticalScrollBar().
                addAdjustmentListener(scrollListener);
    }

    public void removeScrollListener() {
        myScrollPane.getVerticalScrollBar().
                removeAdjustmentListener(scrollListener);
    }

    @Override
    public String getSystemMessage() {
        return systemRole.getText();
    }
}