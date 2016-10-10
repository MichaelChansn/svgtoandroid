package com.moxun.s2v;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.xml.XmlFile;
import com.moxun.s2v.message.ErrorMessage;
import com.moxun.s2v.message.InfoMessage;
import com.moxun.s2v.utils.Logger;
import com.moxun.s2v.utils.ModulesUtil;
import com.moxun.s2v.utils.MyCellRender;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.*;

/**
 * Created by moxun on 15/12/14.
 */
public class GUI {
    private JPanel rootPanel;
    private JComboBox dpiChooser;
    private JTextField svgPath;
    private JButton svgSelectBtn;
    private JComboBox moduleChooser;
    private JButton generateButton;
    private JTextField xmlName;
    private JLabel statusBar;
    private JCheckBox checkBox;
    private JFrame frame;

    private Project project;
    private final String DRAWABLE = "drawable";
    private Set<String> distDirList = new HashSet<String>();
    private ModulesUtil modulesUtil;
    private boolean choiceFiles = false;
    private XmlFile svg;
    private PsiDirectory svgDir;

    public GUI(Project project) {
        this.project = project;
        frame = new JFrame("SVG to VectorDrawable (1.4.3)");
        modulesUtil = new ModulesUtil(project);
        distDirList.clear();
        svgPath.setFocusable(false);
        statusBar.setVisible(false);
        setListener();
        initModules();
    }

    private void initModules() {
        for (String item : modulesUtil.getModules()) {
            moduleChooser.addItem(item);
        }
    }

    private void setListener() {
        checkBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (choiceFiles != checkBox.isSelected()) {
                    svgPath.setText("");
                    xmlName.setText("");
                }
                choiceFiles = checkBox.isSelected();
            }
        });

        svgPath.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                svgPath.setBackground(Color.YELLOW);
            }

            @Override
            public void focusLost(FocusEvent e) {
                svgPath.setBackground(Color.WHITE);
            }
        });

        xmlName.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                xmlName.setBackground(Color.YELLOW);
            }

            @Override
            public void focusLost(FocusEvent e) {
                xmlName.setBackground(Color.WHITE);
            }
        });

        svgSelectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSVGChooser();
                check();
            }
        });

        moduleChooser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dpiChooser.setRenderer(new MyCellRender(modulesUtil.getExistDpiDirs(moduleChooser.getSelectedItem().toString())));
            }
        });

        generateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String moduleName = (String) moduleChooser.getSelectedItem();
                if (moduleName != null) {
                    PsiDirectory resDir = modulesUtil.getResDir(moduleName);
                    if (resDir != null) {
                        Logger.debug("Got res dir " + resDir.getVirtualFile().getPath());
                        Logger.debug("Existing drawable dirs " + modulesUtil.getDrawableDirs(resDir));
                    }
                }
                if (modulesUtil.isAndroidProject()) {
                    if (check() && !choiceFiles) {
                        Transformer transformer = new Transformer.Builder()
                                .setProject(project)
                                .setSVG(svg)
                                .setDpi((String) dpiChooser.getSelectedItem())
                                .setModule(moduleName)
                                .setXmlName(xmlName.getText())
                                .create();

                        transformer.transforming(new Transformer.CallBack() {
                            @Override
                            public void onComplete(XmlFile dist) {
                                transformer.writeXmlToDirAndOpen(dist);
                            }
                        });
                    } else if (check() && choiceFiles) {
                        for (PsiFile svg : svgDir.getFiles()) {
                            if (svg != null && !svg.isDirectory() && svg.getName().endsWith(".svg")) {
                                Transformer transformer = new Transformer.Builder()
                                        .setProject(project)
                                        .setSVG((XmlFile) svg)
                                        .setDpi((String) dpiChooser.getSelectedItem())
                                        .setModule(moduleName)
                                        .setXmlName(svg.getName().replace(".svg", ".xml"))
                                        .create();

                                Transformer.CallBack callBack = new Transformer.CallBack() {
                                    @Override
                                    public void onComplete(XmlFile dist) {
                                        transformer.writeXmlToDir(dist);
                                    }
                                };
                                transformer.transforming(callBack);
                            }
                        }
                        InfoMessage.show(project, "Generating succeeded!");
                    }
                    frame.dispose();
                } else {
                    ErrorMessage.show(project, "Current project is not an Android project!");
                    frame.dispose();
                }
            }
        });
    }

    private void showSVGChooser() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(!choiceFiles, choiceFiles, false, false, false, false);
        VirtualFile virtualFile = FileChooser.chooseFile(descriptor, project, null);
        if (virtualFile != null) {
            if (!virtualFile.isDirectory() && virtualFile.getName().endsWith("svg")) {
                svg = (XmlFile) PsiManager.getInstance(project).findFile(virtualFile);
                //got *.svg file as xml
                svgPath.setText(virtualFile.getPath());
                xmlName.setEditable(true);
                xmlName.setText("vector_drawable_" + getValidName(svg.getName().split("\\.")[0]) + ".xml");
            } else if (virtualFile.isDirectory()) {
                svgDir = PsiManager.getInstance(project).findDirectory(virtualFile);
                svgPath.setText(virtualFile.getPath());
                xmlName.setEditable(false);
                xmlName.setText("keep origin name");
            }
        }
        frame.setAlwaysOnTop(true);
    }

    private String getValidName(String s) {
        char[] chars = s.toLowerCase().replaceAll("\\s*", "").toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetter(chars[i])) {
                chars[i] = '_';
            }
        }
        return String.valueOf(chars);
    }

    private void showXMLChooser() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        VirtualFile virtualFile = FileChooser.chooseFile(descriptor, project, null);
        if (virtualFile != null) {
            if (virtualFile.isDirectory() && virtualFile.getName().startsWith(DRAWABLE)) {
                PsiDirectory directory = PsiDirectoryFactory.getInstance(project).createDirectory(virtualFile);
                PsiDirectory[] dirs = directory.getParentDirectory().getSubdirectories();
                for (PsiDirectory dir : dirs) {
                    if (dir.isDirectory() && dir.getName().contains(DRAWABLE)) {
                        System.out.println(dir.getName() + " is dist dir");
                        if (dir.getName().equals(DRAWABLE)) {
                            distDirList.add("nodpi");
                        } else {
                            String[] tmp = dir.getName().split("-");
                            if (tmp.length == 2) {
                                distDirList.add(tmp[1]);
                            }
                        }
                    }
                }
                System.out.println(distDirList.toString());
                //String template = FileTemplateManager.getInstance(project).findInternalTemplate("vector").getText();
                //XmlFile xml = (XmlFile) PsiFileFactory.getInstance(project).createFileFromText("export.xml", StdFileTypes.XML,template);
                //got *.xml file as XmlFile
                //directory.add(xml);
                //System.out.println(xml.toString());
            } else {
                System.out.println(virtualFile.getName());
                //not a drawable dir
            }
        }
    }

    public void show() {
        frame.setContentPane(rootPanel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(frame.getParent());
        frame.setVisible(true);

        //UpdateUtil.checkUpdate(statusBar);
    }

    private boolean check() {
        boolean pass = false;
        if (svgPath.getText().isEmpty()) {
            svgPath.setBackground(new Color(0xff, 0xae, 0xb9));
            pass = false;
        } else {
            svgPath.setBackground(Color.WHITE);
            pass = true;
        }

        if (xmlName.getText().isEmpty()) {
            xmlName.setBackground(new Color(0xff, 0xae, 0xb9));
            pass = false;
        } else {
            xmlName.setBackground(Color.WHITE);
            pass = true;
        }
        return pass;
    }
}
