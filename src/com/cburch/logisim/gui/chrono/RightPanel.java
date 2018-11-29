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
package com.cburch.logisim.gui.chrono;
import static com.cburch.logisim.gui.chrono.Strings.S;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import com.cburch.logisim.gui.log.Model;
import com.cburch.logisim.gui.log.Signal;

// Right panel has timeline on top and multiple Waveform components.
public class RightPanel extends JPanel {

  private static final int WAVE_HEIGHT = ChronoPanel.SIGNAL_HEIGHT;
  private static final int EXTRA_SPACE = 40; // at right side, to allow for label overhang
  private static final int CURSOR_GAP = 20; // don't put cursor too close to sides side
  private static final int TIMELINE_SPACING = 80; // timeline marker pixel spacing

	private ChronoPanel chronoPanel;
  DefaultListSelectionModel selectionModel;
  private Model model;

	private ArrayList<Waveform> rows = new ArrayList<>();

	private int curX = Integer.MAX_VALUE; // pixel coordinate of cursor, or MAX_VALUE to pin at right
	private long curT = Long.MAX_VALUE; // time of cursor, or MAX_VALUE to pin at right
	private int tickWidth = 20; // display width of one time unit (timeScale simulated nanoseconds)
  private int slope; // display width of transitions, when duration of signal permits
  private long tStartDraw = 0; // drawing started at this time, inclusive
  private long tNextDraw = 0; // done drawing up to this time, exclusive 
  private int width, height;
  private MyListener myListener = new MyListener();

	public RightPanel(ChronoPanel p, ListSelectionModel m) {
		chronoPanel = p;
    selectionModel = (DefaultListSelectionModel)m;
    model = p.getModel();
		slope = (tickWidth < 12) ? tickWidth / 3 : 4;
		configure();
	}

	// public RightPanel(RightPanel oldPanel, ListSelectionModel m) {
  //   try { throw new Exception(); }
  //   catch (Exception e) { e.printStackTrace(); }
	// 	chronoPanel = oldPanel.chronoPanel;
  //   selectionModel = (DefaultListSelectionModel)m;
	// 	tickWidth = oldPanel.tickWidth;
	// 	slope = (tickWidth < 12) ? tickWidth / 3 : 4;
	// 	curX = oldPanel.curX;
  //   curT = oldPanel.curT;
	// 	configure();
	// }

	private void configure() {
    int n = model.getSignalCount();
		height = ChronoPanel.HEADER_HEIGHT + n * ChronoPanel.SIGNAL_HEIGHT;

		setBackground(Color.WHITE);

    long timeScale = model.getTimeScale();
    int numTicks = (int)(((model.getEndTime()-model.getStartTime()) + timeScale - 1) / timeScale);
		width = tickWidth * numTicks + EXTRA_SPACE;

		addMouseListener(myListener);
		addMouseMotionListener(myListener);
		addMouseWheelListener(myListener);

    updateSignals();
	}

  int indexOf(Signal s) {
    int n = rows.size();
    for (int i = 0; i < n; i++) {
      Waveform w = rows.get(i);
      if (w.signal == s)
        return i;
    }
    return -1;
  }

  public void updateSignals() {
    int n = model.getSignalCount();
    for (int i = 0; i < n; i++) {
      Signal s = model.getSignal(i);
      int idx = indexOf(s);
      if (idx < 0) {
        // new signal, add in correct position
        rows.add(i, new Waveform(s));
      } else if (idx != i) {
        // existing signal, move to correct position
        rows.add(i, rows.remove(idx));
      }
		}
    if (rows.size() > n)
      rows.subList(n, rows.size()).clear();
    tStartDraw = tNextDraw = -1; // forces updateWaveforms() to refresh waveforms
    updateWaveforms();
  }

  public void updateWaveforms() {
    long t0 = model.getStartTime();
    long t1 = model.getEndTime();
    if (t0 == tStartDraw && t1 == tNextDraw)
      return; // already drawn all signal values
    tStartDraw = t0;
    tNextDraw = t1;
    updateSize();
    flushWaveforms();
    repaint();
  }

  private void updateSize() {
    long timeScale = model.getTimeScale();
    int numTicks = (int)(((tNextDraw-tStartDraw) + timeScale - 1) / timeScale);

    int m = model.getSignalCount();
		height = ChronoPanel.HEADER_HEIGHT + m * ChronoPanel.SIGNAL_HEIGHT;
		width = tickWidth * numTicks + EXTRA_SPACE;

    Dimension d = getPreferredSize();
    if (d.width == width && d.height == height)
      return;

    int oldWidth = d.width;
    JViewport v = chronoPanel.getRightViewport();
    JScrollBar sb = chronoPanel.getHorizontalScrollBar();
    Rectangle oldR = v == null ? null : v.getViewRect();

    d.width = width;
    d.height = height;
    setPreferredSize(d); // necessary for scrollbar
    revalidate();

    if (sb == null || v == null || sb.getValueIsAdjusting())
      return;

    // if cursor is off screen, but right edge was on screen, scroll to max position
    // if cursor is on screen and right edge was on screen, scroll as far as possible while still keeping cursor on screen
    // .....(.....|....... )
    // .....(........|.... )
    // ...(.|..........)..
    // (.|..........).....
    // (...|...     )     
    // ^                   ^
    // never go below left=0 (0%) or above right=width-1 (100%)
    // try to not go above cursor-CURSOR_GAP

    Rectangle r = v.getViewRect(); // has this updated yet?

    boolean edgeVisible = (oldWidth <= oldR.x + oldR.width);
    boolean cursorVisible = (oldR.x <= curX && curX <= oldR.x + oldR.width);
    if (cursorVisible && edgeVisible) {
      // cursor was on screen, keep it on screen
      r.x = Math.max(oldR.x, curX - CURSOR_GAP);
      r.width = Math.max(r.width, width - r.x);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() { scrollRectToVisible(r); }
      });
    } else if (edgeVisible) {
      // right edge was on screen, keep it on screen
      r.x = Math.max(0, width - r.width);
      r.width = width - r.x;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() { scrollRectToVisible(r); }
      });
    } else {
      // do nothing
    }
  }
  
  static long snapToPixel(long delta, long t) {
    // Each pixel covers delta=timeScale/tickWidth amount of time, and
    // rather than just truncating, we can try to find a good rounding.
    // e.g. t = 1234567, delta = 100
    // and the best point within range [1234567, 1234667) is 12345600
    long s = 1;
    while (s < t && ((t+10*s-1)/(10*s))*(10*s) < t+delta)
      s *= 10;
    return ((t+s-1)/s)*s;
  }

	public void setSignalCursorX(int posX) {
    double f = model.getTimeScale() / (double)tickWidth;
    curX = Math.max(0, posX);
    // curT = Math.max(0L, (long)((curX - slope/2.0) * timeScale / tickWidth));
    long t0 = model.getStartTime();
    curT = + Math.max(t0, snapToPixel((long)f, (long)(t0 + curX * f)));
    if (curT >= model.getEndTime()) {
      curX = Integer.MAX_VALUE; // pin to right side
      curT = Long.MAX_VALUE; // pin to right side
    }
    repaint(); // todo: partial repaint
	}

  public int getSignalCursorX() {
    long timeScale = model.getTimeScale();
    return curX == Integer.MAX_VALUE
        ? (int)((model.getEndTime()-model.getStartTime()-1.0)*tickWidth/timeScale)
        : curX;
  }

  public long getCurrentTime() {
    return curT == Long.MAX_VALUE ? model.getEndTime()-1 : curT;
  }

  public void changeSpotlight(Signal oldSignal, Signal newSignal) {
    if (oldSignal != null) {
      Waveform w = rows.get(oldSignal.idx);
      w.flush();
      repaint(w.getBounds());
    }
    if (newSignal != null) {
      Waveform w = rows.get(newSignal.idx);
      w.flush();
      repaint(w.getBounds());
    }
  }

  public void updateSelected(int firstIdx, int lastIdx) {
    for (int i = firstIdx; i <= lastIdx; i++) {
      Waveform w = rows.get(i);
      boolean selected = selectionModel.isSelectedIndex(i);
      if (selected != w.selected) {
        w.selected = selected;
        w.flush();
        repaint(w.getBounds());
      }
    }
  }

  public void flushWaveforms() {
    for (Waveform w : rows)
      w.flush();
  }

  private static final Font MSG_FONT = new Font("Serif", Font.ITALIC, 12);
  private static final Font TIME_FONT = new Font("Serif", Font.ITALIC, 9);

  @Override
  public void paintComponent(Graphics gr) {
    Graphics2D g = (Graphics2D)gr;

    /* Anti-aliasing changes from https://github.com/hausen/logisim-evolution */
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);

    g.setColor(Color.WHITE);
    g.fillRect(0, 0, getWidth(), getHeight()); // entire viewport, not just (width, height)
    g.setColor(Color.BLACK);
    if (rows.size() == 0) {
      Font f = g.getFont();
      g.setFont(MSG_FONT);
      String lines = S.get("NoSignalsSelected");
      int x = 15, y = 15;
      for (String s : lines.split("\\|")) {
        g.drawString(s.trim(), x, y);
        y += 14;
      }
      g.setFont(f);
      return;
    }
    for (Waveform w : rows)
      w.paintWaveform(g);
    int h = ChronoPanel.HEADER_HEIGHT;
    g.setColor(Color.LIGHT_GRAY);
    g.fillRect(0, 0, width, ChronoPanel.HEADER_HEIGHT);
    paintTimeline(g);
    paintCursor(g);
  }

  public static final long[] unit = new long[] { 10, 20, 25, 50 };
  public static final long[] subd = new long[] { 4, 4, 5, 5 };
  public void paintTimeline(Graphics2D g) {
    long timeScale = model.getTimeScale();
    double timePerPixel = timeScale / (double)tickWidth;
    double pixelPerTime = tickWidth / (double)timeScale;

    // Pick the smallest unit among:
    //   10,  20,  25,  50
    //   100, 200, 250, 500
    //   etc., such that labels are at least TIMELINE_SPACING pixels apart.

    // todo: in clock and step mode, maybe use timeScale as unit?

    long b = 1;
    int j = 0;
    while ((int)(unit[j]*b*pixelPerTime) < TIMELINE_SPACING) {
      if (++j >= unit.length) {
        b *= 10;
        j = 0;
      }
    }

    long divMajor = unit[j]*b;
    long numMinor = subd[j];
    long divMinor = divMajor / numMinor;
    
    Font f = g.getFont();
    g.setFont(TIME_FONT);

    long t0 = model.getStartTime();
    long tL = (t0 / divMajor) * divMajor;
    int h = ChronoPanel.HEADER_HEIGHT - ChronoPanel.GAP;
    g.setColor(Color.BLACK);
    g.drawLine(0, h, width, h);
    for (int i = 0; true; i++) {
      long t = tL + divMinor * i;
      if (t < t0)
        continue;
      int x = (int)((t-t0) * pixelPerTime);
      if (x >= width)
        break;
      if (i % numMinor == 0) {
        if (x + EXTRA_SPACE <= width) {
          String s = Model.formatDuration(t);
          g.drawString(s, x, h/2);
        }
        g.drawLine(x, h*2/3, x, h);
      } else {
        g.drawLine(x, h-2, x, h);
      }
    }

    g.setFont(f);
  }

	private void paintCursor(Graphics2D g) {
    int x = getSignalCursorX();
    long t = getCurrentTime();

    Font f = g.getFont();
    g.setFont(TIME_FONT);

    String s = Model.formatDuration(t);
    FontMetrics fm = g.getFontMetrics();
    Rectangle2D r = fm.getStringBounds(s, g);
    //g.setColor(Color.LIGHT_GRAY);
    g.setColor(Color.YELLOW);
    int y = (ChronoPanel.HEADER_HEIGHT - ChronoPanel.GAP)/2;
    g.fillRect(x+2 + (int)r.getX()-1, y + (int)r.getY()-1, (int)r.getWidth()+2, (int)r.getHeight()+2);
    g.setColor(Color.RED);
    g.drawString(s, x+2, y);
		g.setStroke(new BasicStroke(1));
		g.drawLine(x, 0, x, getHeight());
	}

	private class MyListener extends MouseAdapter {
    boolean shiftDrag, controlDrag, subtracting;

    Signal getSignal(int y, boolean force) {
      int idx = (y - ChronoPanel.HEADER_HEIGHT) / WAVE_HEIGHT;
      int n = model.getSignalCount();
      if (idx < 0 && force)
        idx = 0;
      else if (idx >= n && force)
        idx = n - 1;
      return (idx < 0 || idx >= n) ? null : model.getSignal(idx);
    }

		@Override
		public void mouseMoved(MouseEvent e) {
      chronoPanel.changeSpotlight(getSignal(e.getY(), false));
		}

		@Override
		public void mouseEntered(MouseEvent e) {
      chronoPanel.changeSpotlight(getSignal(e.getY(), false));
		}

		@Override
		public void mouseExited(MouseEvent e) {
      chronoPanel.changeSpotlight(null);
		}

		@Override
		public void mousePressed(MouseEvent e) {
      if (SwingUtilities.isLeftMouseButton(e)) {
        chronoPanel.setSignalCursorX(e.getX());
        Signal signal = getSignal(e.getY(), false);
        if (signal == null) {
          shiftDrag = controlDrag = subtracting = false;
          return;
        }
        shiftDrag = e.isShiftDown();
        controlDrag = !shiftDrag && e.isControlDown();
        subtracting = controlDrag && selectionModel.isSelectedIndex(signal.idx);
        selectionModel.setValueIsAdjusting(true);
        if (shiftDrag) {
          if (selectionModel.getAnchorSelectionIndex() < 0)
            selectionModel.setAnchorSelectionIndex(0);
          selectionModel.setLeadSelectionIndex(signal.idx);
        } else if (controlDrag) {
          if (subtracting)
            selectionModel.removeSelectionInterval(signal.idx, signal.idx);
          else
            selectionModel.addSelectionInterval(signal.idx, signal.idx);
        } else {
          selectionModel.setSelectionInterval(signal.idx, signal.idx);
        }
      }
		}

		@Override
		public void mouseDragged(MouseEvent e) {
      chronoPanel.changeSpotlight(getSignal(e.getY(), false));
      if (SwingUtilities.isLeftMouseButton(e)) {
        chronoPanel.setSignalCursorX(e.getX());
        if (!selectionModel.getValueIsAdjusting())
          return;
        Signal signal = getSignal(e.getY(), false);
        if (signal == null)
          return;
        selectionModel.setLeadSelectionIndex(signal.idx);
      }
		}

		@Override
		public void mouseReleased(MouseEvent e) {
      if (SwingUtilities.isLeftMouseButton(e)) {
        if (!selectionModel.getValueIsAdjusting())
          return;
        Signal signal = getSignal(e.getY(), true);
        if (signal == null)
          return;
        int idx = selectionModel.getAnchorSelectionIndex();
        if (idx < 0) {
          idx = signal.idx;
          selectionModel.setAnchorSelectionIndex(signal.idx);
        }
        selectionModel.setLeadSelectionIndex(signal.idx);
        shiftDrag = controlDrag = subtracting = false;
        selectionModel.setValueIsAdjusting(false);
      }
    }

		@Override
		public void mouseClicked(MouseEvent e) {
      if (SwingUtilities.isRightMouseButton(e)) {
        List<Signal> signals = chronoPanel.getLeftPanel().getSelectedValuesList();
        if (signals.size() == 0) {
          Signal signal = getSignal(e.getY(), false);
          if (signal == null)
            return;
          signals.add(signal);
          PopupMenu m = new PopupMenu(chronoPanel, signals);
          m.doPop(e);
        }
      }
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
      zoom(e.getWheelRotation() > 0 ? -1 : +1, e.getPoint().x);
		}
	}

  private class Waveform {

    private static final int HIGH = ChronoPanel.GAP;
    private static final int LOW = WAVE_HEIGHT - ChronoPanel.GAP;
    private static final int MID = WAVE_HEIGHT / 2;

    final Signal signal;
    private BufferedImage buf;
    boolean selected;

    public Waveform(Signal s) {
      this.signal = s;
    }

    Rectangle getBounds() {
      int y = ChronoPanel.HEADER_HEIGHT + WAVE_HEIGHT * signal.idx;
      return new Rectangle(0, y, width, WAVE_HEIGHT);
    }

    private void drawSignal(Graphics2D g, boolean bold, Color[] colors) {
      g.setStroke(new BasicStroke(bold ? 2 : 1));

      long t0 = model.getStartTime();
      Signal.Iterator cur = signal.new Iterator(t0);

      FontMetrics fm = g.getFontMetrics();

      String max = signal.getFormattedMaxValue();
      String min = signal.getFormattedMinValue();
      int labelWidth = Math.max(fm.stringWidth(max), fm.stringWidth(min));

      double z = tickWidth / (double)model.getTimeScale();
      boolean prevHi = false, prevLo = false;
      Color prevFill = null;
      while (cur.value != null) {
        String v = cur.getFormattedValue();
        int x0 = (int)(z * (cur.time - t0));
        int x1 = (int)(z * (cur.time + cur.duration - t0));
    
        boolean hi = true, lo = true;
        Color lineColor, fillColor;

        if (v.contains("E")) {
          fillColor = colors[3];
          lineColor = colors[4];
        } else if (v.contains("x")) {
          fillColor = colors[5];
          lineColor = colors[6];
        } else if (v.equals(min)) {
          hi = false;
          fillColor = colors[1];
          lineColor = colors[2];
        } else if (v.equals(max)) {
          lo = false;
          fillColor = colors[1];
          lineColor = colors[2];
        } else {
          fillColor = colors[1];
          lineColor = colors[2];
        }

        // __________       _____ __________       ______
        //     \_____\_____/_____X_____/    \_____/
        //    |     |     |     |     |    |     |   

        if (prevFill != null) {
          // draw left transition
          int xt = x0 + Math.min(slope, (x1 - x0)/2);
          // if (xt <= x0 + 3)
          //   xt = x0;
          if (xt == x0) {
            // not enough room for sloped transition
            if (hi) {
              g.setColor(fillColor);
              g.fillRect(x0, HIGH, (x1-x0)+1, LOW - HIGH + 1);
            }
            g.setColor(lineColor);
            g.drawLine(x0, HIGH, x0, LOW);
            if (hi)
              g.drawLine(x0, HIGH, x1, HIGH);
            if (lo)
              g.drawLine(x0, LOW, x1, LOW);
          } else {
            // draw sloped transition
            if (prevHi && prevLo && hi && lo) {
              //   ____ _____
              //   ____X_____
              //
              g.setColor(prevFill);
              g.fillPolygon(
                  new int[] { x0, x0 + (xt-x0)/2, x0 },
                  new int[] { HIGH, MID, LOW+1 },
                  3);
              g.setColor(fillColor);
              g.fillPolygon(
                  new int[] { x0 + (xt-x0)/2, xt, x1, x1, xt },
                  new int[] { MID, HIGH, HIGH, LOW+1, LOW+1 },
                  5);
              g.setColor(lineColor);
              g.drawLine(x0, HIGH, xt, LOW);
              g.drawLine(x0, LOW, xt, HIGH);
              g.drawLine(xt, HIGH, x1, HIGH);
              g.drawLine(xt, LOW, x1, LOW);
            } else if (!hi) {
              //   _____ 
              //   _ _ _\_____
              //
              g.setColor(prevFill);
              g.fillPolygon(
                  new int[] { x0, xt, x0 },
                  new int[] { HIGH, LOW+1, LOW+1 },
                  3);
              g.setColor(lineColor);
              g.drawLine(x0, HIGH, xt, LOW);
              g.drawLine(prevLo ? x0 : xt, LOW, x1, LOW);
            } else if (!lo) {
              //   _ _ _ _____
              //   _____/
              //
              if (prevHi) {
                g.setColor(prevFill);
                g.fillPolygon(
                    new int[] { x0, xt, x0 },
                    new int[] { HIGH, HIGH, LOW+1 },
                    3);
              }
              g.setColor(fillColor);
              g.fillPolygon(
                  new int[] { x0, xt, x1, x1, x0 },
                  new int[] { LOW+1, HIGH, HIGH, LOW+1 },
                  4);
              g.setColor(lineColor);
              g.drawLine(x0, LOW, xt, HIGH);
              g.drawLine(prevHi ? x0 : xt, HIGH, x1, HIGH);
            } else if (!prevHi) {
              //         _____
              //   _____/_____
              //
              g.setColor(fillColor);
              g.fillPolygon(
                  new int[] { x0, xt, x1, x1, x0 },
                  new int[] { LOW+1, HIGH, HIGH, LOW+1 },
                  4);
              g.setColor(lineColor);
              g.drawLine(x0, LOW, x1, LOW);
              g.drawLine(x0, LOW, xt, HIGH);
              g.drawLine(xt, HIGH, x1, HIGH);
            } else if (!prevLo) {
              //   ___________
              //        \_____
              //
              g.setColor(prevFill);
              g.fillPolygon(
                  new int[] { x0, xt, x0 },
                  new int[] { HIGH, LOW+1, LOW+1 },
                  3);
              g.setColor(fillColor);
              g.fillPolygon(
                  new int[] { x0, x1, x1, xt},
                  new int[] { HIGH, HIGH, LOW+1, LOW+1 },
                  4);
              g.setColor(lineColor);
              g.drawLine(x0, HIGH, x1, HIGH);
              g.drawLine(x0, HIGH, xt, LOW);
              g.drawLine(xt, LOW, x1, LOW);
            } else {
              System.out.println("huh?");
            }
          }
        } else {
          // first point, no left transition
          if (hi) {
            g.setColor(fillColor);
            g.fillRect(x0, HIGH, (x1-x0)+1, LOW - HIGH + 1);
            g.setColor(lineColor);
            g.drawLine(x0, HIGH, x1, HIGH);
          }
          if (lo) {
            g.setColor(lineColor);
            g.drawLine(x0, LOW, x1, LOW);
          }
        }

        if (x1 - x0 > labelWidth) {
          g.setColor(Color.BLACK);
          g.drawString(v, x0 + 6, MID+5);
        }

        prevHi = hi;
        prevLo = lo;
        prevFill = fillColor;
        if (!cur.advance())
          break;
      }
    }

    private void createOffscreen() {
      buf = (BufferedImage)createImage(width, WAVE_HEIGHT);
      Graphics2D g = buf.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
          RenderingHints.VALUE_STROKE_DEFAULT);
      /* Anti-aliasing changes from https://github.com/hausen/logisim-evolution */
      Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);
      boolean bold = model.getSpotlight() == signal;
      Color[] colors = chronoPanel.rowColors(signal.info, selected);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, width, ChronoPanel.GAP-1);
      g.fillRect(0, LOW, width, ChronoPanel.GAP-1);
      g.setColor(colors[0]);
      g.fillRect(0, HIGH, width, LOW - HIGH);
      g.setColor(Color.BLACK);
      drawSignal(g, bold, colors);
      g.dispose();
    }

    public void paintWaveform(Graphics2D g) {
      if (buf == null) // todo: reallocating image each time seems silly
        createOffscreen();
      int y = ChronoPanel.HEADER_HEIGHT + WAVE_HEIGHT * signal.idx;
      g.drawImage(buf, null, 0, y);
    }

    public void flush() {
      buf = null;
    }

  }

  // todo: later
  public void zoom(int sens, int posX) {
    //     int nbrOfTick = curX / tickWidth;
    // 
    //     tickWidth += sens;
    //     if (tickWidth <= 1)
    //       tickWidth = 1;
    // 
    //     // make the curX follow the zoom
    //     int newPosX = nbrOfTick * tickWidth;
    //     curX = newPosX;
    //     // set the cusor position
    //     cursor.setPosition(newPosX);
    // 
    //     // Scrollbar follow the zoom
    //     int scrollBarCursorPos = cursor.getPosition()
    //         - (chronoPanel.getVisibleSignalsWidth() / 2);
    // 
    //     // zoom on every signals
    //     for (Waveform sDraw : rows) {
    //       sDraw.setTickWidth(tickWidth);
    //     }
    // 
    //     // zoom on the timeline
    //     timeline.setTickWidth(tickWidth, 2 /* chronoPanel.getNbrOfTick() */);
    // 
    //     computeSize();
    // 
    //     // force redraw everything
    //     SwingUtilities.updateComponentTreeUI(chronoPanel);
    // 
    //     // scrollbar position
    //     chronoPanel.setScrollbarPosition(scrollBarCursorPos);
  }

  // todo: later
  public void adjustmentValueChanged(int value) {
    //    float posPercent = (float) value / (float) getSignalWidth();
    //    int i = Math.round(/* chronoPanel.getNbrOfTick()*/ 2 * posPercent);
    //    i = i > 5 ? i - 5 : 0;
    //    for (Waveform sDraw : rows) {
    //      sDraw.flush();
    //      sDraw.repaint();
    //    }
  }
}