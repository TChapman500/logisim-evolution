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

package com.cburch.logisim.std.gates;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

import com.bfh.logisim.hdlgenerator.HDLSupport;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Expressions;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.util.GraphicsUtil;

class OddParityGate extends AbstractGate {

  public static OddParityGate FACTORY = new OddParityGate();

  private OddParityGate() {
    super("Odd Parity", S.getter("oddParityComponent"));
    setRectangularLabel("2k+1");
    setIconNames("parityOddGate.gif");
  }

  @Override
  protected Expression computeExpression(Expression[] inputs, int numInputs) {
    Expression ret = inputs[0];
    for (int i = 1; i < numInputs; i++) {
      ret = Expressions.xor(ret, inputs[i]);
    }
    return ret;
  }

  @Override
  protected Value computeOutput(Value[] inputs, int numInputs,
      InstanceState state) {
    return GateFunctions.computeOddParity(inputs, numInputs);
  }

  @Override
  protected Value getIdentity() {
    return Value.FALSE;
  }

  @Override
  public HDLSupport getHDLSupport(HDLSupport.ComponentContext ctx) {
    if (ctx.lang.equals("VHDL"))
      return GateVhdlGenerator.forXor(ctx);
    else
      return GateVerilogGenerator.forXor(ctx);
  }

  @Override
  protected void paintDinShape(InstancePainter painter, int width,
      int height, int inputs) {
    paintRectangular(painter, width, height);
  }

  @Override
  public void paintIconRectangular(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    g.setColor(Color.black);
    g.drawRect(1, 2, 16, 16);
    Font old = g.getFont();
    g.setFont(old.deriveFont(9.0f));
    GraphicsUtil.drawCenteredText(g, "2k", 9, 6);
    GraphicsUtil.drawCenteredText(g, "+1", 9, 13);
    g.setFont(old);
  }

  @Override
  public void paintIconShaped(InstancePainter painter) {
    paintIconRectangular(painter);
  }

  @Override
  protected void paintShape(InstancePainter painter, int width, int height) {
    paintRectangular(painter, width, height);
  }

}
