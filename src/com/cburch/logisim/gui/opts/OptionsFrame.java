/**
 * This file is part of Logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Logisim-evolution.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + Haute École Spécialisée Bernoise
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 *   + REDS Institute - HEIG-VD, Yverdon-les-Bains, Switzerland
 *     http://reds.heig-vd.ch
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.gui.opts;
import static com.cburch.logisim.gui.opts.Strings.S;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.TableLayout;
import com.cburch.logisim.util.WindowMenuItemManager;

public class OptionsFrame extends LFrame.Dialog {
  private class MyListener implements LibraryListener, LocaleListener {
    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.SET_NAME) {
        computeTitle();
        windowManager.localeChanged();
      }
    }

    public void localeChanged() {
      computeTitle();
      for (int i = 0; i < panels.length; i++) {
        tabbedPane.setTitleAt(i, panels[i].getTitle());
        tabbedPane.setToolTipTextAt(i, panels[i].getToolTipText());
        panels[i].localeChanged();
      }
      windowManager.localeChanged();
    }
  }

  private class WindowMenuManager extends WindowMenuItemManager
    implements LocaleListener {
    WindowMenuManager() {
      super(S.get("optionsFrameMenuItem"), false);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return OptionsFrame.this;
    }

    public void localeChanged() {
      String title = project.getLogisimFile().getDisplayName();
      setText(S.fmt("optionsFrameMenuItem", title));
    }
  }

  private void computeTitle() {
    LogisimFile file = project.getLogisimFile();
    String name = file == null ? "???" : file.getName();
    String title = S.fmt("optionsFrameTitle", name);
    setTitle(title);
  }

  private static final long serialVersionUID = 1L;
  // private LogisimFile file;
  private MyListener myListener = new MyListener();

  private WindowMenuManager windowManager = new WindowMenuManager();
  private OptionsPanel[] panels;
  private JTabbedPane tabbedPane;

  public OptionsFrame(Project project) {
    super(project);
    // this.file = project.getLogisimFile();
    project.addLibraryWeakListener(/*null,*/ myListener);
    project.addProjectWeakListener(this, new ProjectListener() {
      @Override
      public void projectChanged(ProjectEvent event) {
        int action = event.getAction();
        if (action == ProjectEvent.ACTION_SET_STATE) {
          // file = project.getLogisimFile();
          computeTitle();
        }
      }
    });

    panels = new OptionsPanel[] {
      new SimulateOptions(this),
      new ToolbarOptions(this), // index=1: see setSelectedIndex(1) below
      new MouseOptions(this),
      new RevertPanel(this)
    };
    tabbedPane = new JTabbedPane();
    for (int index = 0; index < panels.length; index++) {
      OptionsPanel panel = panels[index];
      tabbedPane.addTab(panel.getTitle(), null, panel,
          panel.getToolTipText());
    }

    Container contents = getContentPane();
    tabbedPane.setPreferredSize(new Dimension(450, 300));
    contents.add(tabbedPane, BorderLayout.CENTER);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();

    setLocationRelativeTo(project.getFrame());
  }

  public Options getOptions() {
    return project.getLogisimFile().getOptions();
  }

  OptionsPanel[] getPrefPanels() {
    return panels;
  }

  @Override
  public void setVisible(boolean value) {
    if (value) {
      windowManager.frameOpened(this);
    }
    super.setVisible(value);
  }

  public void showToolbarPanel() {
    tabbedPane.setSelectedIndex(1);
    setVisible(true);
  }

  static class RevertPanel extends OptionsPanel {
    private class MyListener implements ActionListener {
      public void actionPerformed(ActionEvent event) {
        Object src = event.getSource();
        if (src == revert) {
          getProject().doAction(LogisimFileActions.revertDefaults());
        }
      }
    }

    private MyListener myListener = new MyListener();
    private JButton revert = new JButton();

    public RevertPanel(OptionsFrame window) {
      super(window);

      setLayout(new TableLayout(1));
      JPanel buttonPanel = new JPanel();
      buttonPanel.add(revert);
      revert.addActionListener(myListener);
      add(buttonPanel);
    }

    @Override
    public String getHelpText() {
      return S.get("revertHelp");
    }

    @Override
    public String getTitle() {
      return S.get("revertTitle");
    }

    @Override
    public void localeChanged() {
      revert.setText(S.get("revertButton"));
    }
  }
}
