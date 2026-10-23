import {
  MathAccentNode,
  MathBigOpNode,
  MathDelimitedNode,
  MathFracNode,
  MathMatrixNode,
  MathNode,
  MathOperatorNode,
  MathRowNode,
  MathScriptedNode,
  MathSqrtNode,
  MathTextNode,
} from './MathNode';

export class MathLayoutBox {
  kind: string = 'group';
  width: number = 1;
  ascent: number = 1;
  descent: number = 0;
  text: string = '';
  fontSize: number = 16;
  children: MathLayoutBox[] = [];
  childX: number[] = [];
  childY: number[] = [];
  lineVisible: boolean = false;
  environment: string = '';
  leftFence: string = '';
  rightFence: string = '';
}

export class MathCanvasLayout {
  private context: CanvasRenderingContext2D;
  private baseFontSize: number;
  private color: string;

  constructor(context: CanvasRenderingContext2D, fontSize: number, color: string) {
    this.context = context;
    this.baseFontSize = Math.max(fontSize, 1);
    this.color = color;
  }

  layout(node: MathNode): MathLayoutBox {
    return this.layoutNode(node, this.baseFontSize);
  }

  draw(box: MathLayoutBox, x: number, baseline: number): void {
    this.context.fillStyle = this.color;
    this.context.strokeStyle = this.color;
    this.context.textAlign = 'left';
    this.context.textBaseline = 'alphabetic';
    this.drawBox(box, x, baseline);
  }

  private layoutNode(node: MathNode, fontSize: number): MathLayoutBox {
    if (node instanceof MathTextNode) {
      return this.textBox(node.value, fontSize);
    }
    if (node instanceof MathRowNode) {
      return this.rowBox(node, fontSize);
    }
    if (node instanceof MathFracNode) {
      return this.fracBox(node, fontSize);
    }
    if (node instanceof MathSqrtNode) {
      return this.sqrtBox(node, fontSize);
    }
    if (node instanceof MathMatrixNode) {
      return this.matrixBox(node, fontSize);
    }
    if (node instanceof MathScriptedNode) {
      return this.scriptedBox(node, fontSize);
    }
    if (node instanceof MathOperatorNode) {
      return this.operatorBox(node, fontSize);
    }
    if (node instanceof MathDelimitedNode) {
      return this.delimitedBox(node, fontSize);
    }
    if (node instanceof MathAccentNode) {
      return this.accentBox(node, fontSize);
    }
    if (node instanceof MathBigOpNode) {
      // Display-style bigops (∑/∫/∏) need a taller glyph than body text.
      const scale = node.limits ? 1.85 : 1.4;
      return this.textBox(node.symbol, fontSize * scale);
    }
    return this.textBox('', fontSize);
  }

  private textBox(text: string, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'text';
    box.text = text;
    box.fontSize = fontSize;
    this.useFont(fontSize);
    const metrics = this.context.measureText(text.length === 0 ? ' ' : text);
    box.width = text.length === 0 ? 1 : Math.max(metrics.width, 1);
    box.ascent = Math.max(metrics.actualBoundingBoxAscent, fontSize * 0.78);
    box.descent = Math.max(metrics.actualBoundingBoxDescent, fontSize * 0.22);
    return box;
  }

  private rowBox(node: MathRowNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'row';
    let x = 0;
    for (let index = 0; index < node.children.length; index += 1) {
      const child = this.layoutNode(node.children[index], fontSize);
      box.children.push(child);
      box.childX.push(x);
      box.childY.push(0);
      x += child.width;
      box.ascent = Math.max(box.ascent, child.ascent);
      box.descent = Math.max(box.descent, child.descent);
    }
    box.width = Math.max(x, 1);
    return box;
  }

  private fracBox(node: MathFracNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'frac';
    const childSize = fontSize * 0.9;
    const numerator = this.layoutNode(node.numerator, childSize);
    const denominator = this.layoutNode(node.denominator, childSize);
    const padding = Math.max(fontSize * 0.12, 1);
    const gap = Math.max(fontSize * 0.12, 1);
    const line = Math.max(fontSize * 0.055, 1);
    box.width = Math.max(numerator.width, denominator.width) + padding * 2;
    const numeratorY = -(gap + line * 0.5 + numerator.descent);
    const denominatorY = gap + line * 0.5 + denominator.ascent;
    box.children = [numerator, denominator];
    box.childX = [(box.width - numerator.width) * 0.5, (box.width - denominator.width) * 0.5];
    box.childY = [numeratorY, denominatorY];
    box.ascent = Math.max(1, -numeratorY + numerator.ascent);
    box.descent = Math.max(1, denominatorY + denominator.descent);
    box.lineVisible = node.bar;
    box.fontSize = fontSize;
    return box;
  }

  private sqrtBox(node: MathSqrtNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'sqrt';
    const body = this.layoutNode(node.body, fontSize);
    const rootWidth = Math.max(fontSize * 0.72, 8);
    const overbar = Math.max(fontSize * 0.12, 2);
    box.children.push(body);
    box.childX.push(rootWidth);
    box.childY.push(0);
    box.width = rootWidth + body.width + overbar;
    box.ascent = body.ascent + overbar;
    box.descent = body.descent;
    if (node.index !== null) {
      const rootIndex = this.layoutNode(node.index, fontSize * 0.5);
      box.children.push(rootIndex);
      box.childX.push(0);
      box.childY.push(-body.ascent * 0.62);
      box.ascent = Math.max(box.ascent, body.ascent * 0.62 + rootIndex.ascent);
    }
    box.fontSize = fontSize;
    return box;
  }

  private scriptedBox(node: MathScriptedNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'scripted';
    const base = this.layoutNode(node.base, fontSize);
    box.children.push(base);
    box.childX.push(0);
    box.childY.push(0);
    let scriptWidth = 0;
    const scriptSize = fontSize * 0.64;
    if (node.sup !== null) {
      const sup = this.layoutNode(node.sup, scriptSize);
      const y = -Math.max(base.ascent * 0.58, fontSize * 0.42);
      box.children.push(sup);
      box.childX.push(base.width + fontSize * 0.04);
      box.childY.push(y);
      scriptWidth = Math.max(scriptWidth, sup.width);
      box.ascent = Math.max(box.ascent, -y + sup.ascent);
    }
    if (node.sub !== null) {
      const sub = this.layoutNode(node.sub, scriptSize);
      const y = Math.max(base.descent + sub.ascent * 0.72, fontSize * 0.42);
      box.children.push(sub);
      box.childX.push(base.width + fontSize * 0.04);
      box.childY.push(y);
      scriptWidth = Math.max(scriptWidth, sub.width);
      box.descent = Math.max(box.descent, y + sub.descent);
    }
    box.width = base.width + (scriptWidth > 0 ? fontSize * 0.04 + scriptWidth : 0);
    box.ascent = Math.max(box.ascent, base.ascent);
    box.descent = Math.max(box.descent, base.descent);
    return box;
  }

  private operatorBox(node: MathOperatorNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'operator';
    const symbol = this.textBox(node.symbol, fontSize * 1.85);
    let width = symbol.width;
    let over: MathLayoutBox | null = null;
    let under: MathLayoutBox | null = null;
    if (node.over !== null) {
      over = this.layoutNode(node.over, fontSize * 0.62);
      width = Math.max(width, over.width);
    }
    if (node.under !== null) {
      under = this.layoutNode(node.under, fontSize * 0.62);
      width = Math.max(width, under.width);
    }
    box.width = width;
    box.children.push(symbol);
    box.childX.push((width - symbol.width) * 0.5);
    box.childY.push(0);
    box.ascent = symbol.ascent;
    box.descent = symbol.descent;
    const gap = Math.max(fontSize * 0.08, 1);
    if (over !== null) {
      const y = -(symbol.ascent + gap + over.descent);
      box.children.push(over);
      box.childX.push((width - over.width) * 0.5);
      box.childY.push(y);
      box.ascent = Math.max(box.ascent, -y + over.ascent);
    }
    if (under !== null) {
      const y = symbol.descent + gap + under.ascent;
      box.children.push(under);
      box.childX.push((width - under.width) * 0.5);
      box.childY.push(y);
      box.descent = Math.max(box.descent, y + under.descent);
    }
    return box;
  }

  private delimitedBox(node: MathDelimitedNode, fontSize: number): MathLayoutBox {
    const body = this.layoutNode(node.body, fontSize);
    const fenceWidth = Math.max(fontSize * 0.55, 6);
    const box = new MathLayoutBox();
    box.kind = 'delimited';
    box.fontSize = fontSize;
    box.leftFence = node.left;
    box.rightFence = node.right;
    box.ascent = body.ascent + Math.max(fontSize * 0.08, 1);
    box.descent = body.descent + Math.max(fontSize * 0.08, 1);
    const leftPad = node.left.length > 0 ? fenceWidth : 0;
    const rightPad = node.right.length > 0 ? fenceWidth : 0;
    box.children = [body];
    box.childX = [leftPad];
    box.childY = [0];
    box.width = leftPad + body.width + rightPad;
    return box;
  }

  private accentBox(node: MathAccentNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = `accent:${node.kind}`;
    const body = this.layoutNode(node.body, fontSize);
    box.children = [body];
    box.childX = [0];
    box.childY = [0];
    box.width = body.width;
    box.ascent = body.ascent + Math.max(fontSize * 0.22, 3);
    box.descent = body.descent + (node.kind === 'underline' ? Math.max(fontSize * 0.15, 2) : 0);
    box.fontSize = fontSize;
    return box;
  }

  private matrixBox(node: MathMatrixNode, fontSize: number): MathLayoutBox {
    const box = new MathLayoutBox();
    box.kind = 'matrix';
    box.environment = node.environment;
    box.fontSize = fontSize;
    if (node.rows.length === 0) {
      return box;
    }
    let columnCount = 0;
    for (let row = 0; row < node.rows.length; row += 1) {
      columnCount = Math.max(columnCount, node.rows[row].length);
    }
    const cells: MathLayoutBox[][] = [];
    const columnWidths: number[] = [];
    const rowAscents: number[] = [];
    const rowDescents: number[] = [];
    for (let column = 0; column < columnCount; column += 1) {
      columnWidths.push(0);
    }
    for (let row = 0; row < node.rows.length; row += 1) {
      const cellRow: MathLayoutBox[] = [];
      let ascent = 1;
      let descent = 1;
      for (let column = 0; column < columnCount; column += 1) {
        const cellNode = column < node.rows[row].length ? node.rows[row][column] : new MathTextNode('');
        const cell = this.layoutNode(cellNode, fontSize * 0.9);
        cellRow.push(cell);
        columnWidths[column] = Math.max(columnWidths[column], cell.width);
        ascent = Math.max(ascent, cell.ascent);
        descent = Math.max(descent, cell.descent);
      }
      cells.push(cellRow);
      rowAscents.push(ascent);
      rowDescents.push(descent);
    }
    const columnGap = Math.max(fontSize * 0.65, 6);
    const rowGap = Math.max(fontSize * 0.32, 4);
    const fenceWidth = matrixFenceWidth(node.environment, fontSize);
    let contentWidth = 0;
    for (let column = 0; column < columnCount; column += 1) {
      contentWidth += columnWidths[column];
      if (column + 1 < columnCount) {
        contentWidth += columnGap;
      }
    }
    let contentHeight = 0;
    for (let row = 0; row < cells.length; row += 1) {
      contentHeight += rowAscents[row] + rowDescents[row];
      if (row + 1 < cells.length) {
        contentHeight += rowGap;
      }
    }
    box.width = contentWidth + fenceWidth * 2;
    box.ascent = contentHeight * 0.5;
    box.descent = contentHeight * 0.5;
    let top = -box.ascent;
    for (let row = 0; row < cells.length; row += 1) {
      const rowBaseline = top + rowAscents[row];
      let x = fenceWidth;
      for (let column = 0; column < columnCount; column += 1) {
        const cell = cells[row][column];
        box.children.push(cell);
        box.childX.push(x + (columnWidths[column] - cell.width) * 0.5);
        box.childY.push(rowBaseline);
        x += columnWidths[column] + columnGap;
      }
      top += rowAscents[row] + rowDescents[row] + rowGap;
    }
    return box;
  }

  private drawBox(box: MathLayoutBox, x: number, baseline: number): void {
    if (box.kind === 'text') {
      this.useFont(box.fontSize);
      this.context.fillText(box.text, x, baseline);
      return;
    }
    for (let index = 0; index < box.children.length; index += 1) {
      this.drawBox(box.children[index], x + box.childX[index], baseline + box.childY[index]);
    }
    if (box.kind === 'frac' && box.lineVisible) {
      this.drawLine(x, baseline, x + box.width, baseline, Math.max(box.fontSize * 0.055, 1));
    } else if (box.kind === 'sqrt') {
      this.drawRadical(box, x, baseline);
    } else if (box.kind === 'matrix') {
      this.drawMatrixFences(box, x, baseline);
    } else if (box.kind === 'delimited') {
      this.drawDelimitedFences(box, x, baseline);
    } else if (box.kind.startsWith('accent:')) {
      this.drawAccent(box, x, baseline, box.kind.substring(7));
    }
  }

  private drawDelimitedFences(box: MathLayoutBox, x: number, baseline: number): void {
    const fenceWidth = Math.max(box.fontSize * 0.55, 6);
    const top = baseline - box.ascent;
    const bottom = baseline + box.descent;
    this.context.lineWidth = Math.max(box.fontSize * 0.055, 1);
    if (box.leftFence.length > 0) {
      this.drawFenceGlyph(box.leftFence, x + 1, top, bottom, fenceWidth * 0.75, true);
    }
    if (box.rightFence.length > 0) {
      this.drawFenceGlyph(box.rightFence, x + box.width - 1, top, bottom, fenceWidth * 0.75, false);
    }
  }

  private drawFenceGlyph(
    glyph: string,
    x: number,
    top: number,
    bottom: number,
    width: number,
    left: boolean,
  ): void {
    if (glyph === '[' || glyph === ']') {
      this.drawBracket(x, top, bottom, width * 0.85, left);
      return;
    }
    if (glyph === '{' || glyph === '}') {
      this.drawBrace(x, top, bottom, width, left);
      return;
    }
    if (glyph === '|') {
      this.drawVerticalFence(x, top, bottom, false);
      return;
    }
    if (glyph === '∥') {
      this.drawVerticalFence(x, top, bottom, true);
      return;
    }
    // Default: stretchable parenthesis for (, ), \langle, \rangle, etc.
    this.drawParenthesis(x, top, bottom, width, left);
  }

  private drawRadical(box: MathLayoutBox, x: number, baseline: number): void {
    const rootWidth = Math.max(box.fontSize * 0.72, 8);
    const top = baseline - box.ascent + Math.max(box.fontSize * 0.08, 1);
    const bottom = baseline + box.descent * 0.72;
    this.context.beginPath();
    this.context.lineWidth = Math.max(box.fontSize * 0.065, 1);
    this.context.moveTo(x, baseline - box.fontSize * 0.04);
    this.context.lineTo(x + rootWidth * 0.22, baseline + box.descent * 0.35);
    this.context.lineTo(x + rootWidth * 0.43, bottom);
    this.context.lineTo(x + rootWidth * 0.72, top);
    this.context.lineTo(x + box.width, top);
    this.context.stroke();
  }

  private drawMatrixFences(box: MathLayoutBox, x: number, baseline: number): void {
    const environment = box.environment;
    if (environment === 'matrix' || environment === 'array') {
      return;
    }
    const fenceWidth = matrixFenceWidth(environment, box.fontSize);
    const top = baseline - box.ascent;
    const bottom = baseline + box.descent;
    const leftX = x + 1;
    const rightX = x + box.width - 1;
    this.context.lineWidth = Math.max(box.fontSize * 0.055, 1);
    if (environment === 'bmatrix') {
      this.drawBracket(leftX, top, bottom, fenceWidth * 0.65, true);
      this.drawBracket(rightX, top, bottom, fenceWidth * 0.65, false);
    } else if (environment === 'vmatrix' || environment === 'Vmatrix') {
      this.drawVerticalFence(leftX + fenceWidth * 0.3, top, bottom, environment === 'Vmatrix');
      this.drawVerticalFence(rightX - fenceWidth * 0.3, top, bottom, environment === 'Vmatrix');
    } else if (environment === 'Bmatrix' || environment === 'cases') {
      this.drawBrace(leftX, top, bottom, fenceWidth * 0.75, true);
      if (environment !== 'cases') {
        this.drawBrace(rightX, top, bottom, fenceWidth * 0.75, false);
      }
    } else {
      this.drawParenthesis(leftX, top, bottom, fenceWidth * 0.75, true);
      this.drawParenthesis(rightX, top, bottom, fenceWidth * 0.75, false);
    }
  }

  private drawBracket(x: number, top: number, bottom: number, width: number, left: boolean): void {
    const direction = left ? 1 : -1;
    this.context.beginPath();
    this.context.moveTo(x + direction * width, top);
    this.context.lineTo(x, top);
    this.context.lineTo(x, bottom);
    this.context.lineTo(x + direction * width, bottom);
    this.context.stroke();
  }

  private drawVerticalFence(x: number, top: number, bottom: number, doubled: boolean): void {
    this.drawLine(x, top, x, bottom, this.context.lineWidth);
    if (doubled) {
      this.drawLine(x + this.context.lineWidth * 2.5, top, x + this.context.lineWidth * 2.5, bottom, this.context.lineWidth);
    }
  }

  private drawParenthesis(x: number, top: number, bottom: number, width: number, left: boolean): void {
    const direction = left ? 1 : -1;
    const middle = (top + bottom) * 0.5;
    this.context.beginPath();
    this.context.moveTo(x + direction * width, top);
    this.context.quadraticCurveTo(x, top + (middle - top) * 0.5, x, middle);
    this.context.quadraticCurveTo(x, middle + (bottom - middle) * 0.5, x + direction * width, bottom);
    this.context.stroke();
  }

  private drawBrace(x: number, top: number, bottom: number, width: number, left: boolean): void {
    const direction = left ? 1 : -1;
    const middle = (top + bottom) * 0.5;
    const quarter = (bottom - top) * 0.25;
    this.context.beginPath();
    this.context.moveTo(x + direction * width, top);
    this.context.quadraticCurveTo(x, top + quarter * 0.45, x + direction * width * 0.45, top + quarter);
    this.context.quadraticCurveTo(x + direction * width, middle - quarter * 0.25, x, middle);
    this.context.quadraticCurveTo(x + direction * width, middle + quarter * 0.25, x + direction * width * 0.45, bottom - quarter);
    this.context.quadraticCurveTo(x, bottom - quarter * 0.45, x + direction * width, bottom);
    this.context.stroke();
  }

  private drawAccent(box: MathLayoutBox, x: number, baseline: number, kind: string): void {
    const top = baseline - box.children[0].ascent - Math.max(box.fontSize * 0.08, 1);
    const lineWidth = Math.max(box.fontSize * 0.05, 1);
    if (kind === 'underline') {
      const y = baseline + box.children[0].descent + Math.max(box.fontSize * 0.08, 1);
      this.drawLine(x, y, x + box.width, y, lineWidth);
    } else if (kind === 'hat') {
      this.context.beginPath();
      this.context.lineWidth = lineWidth;
      this.context.moveTo(x, top);
      this.context.lineTo(x + box.width * 0.5, top - box.fontSize * 0.16);
      this.context.lineTo(x + box.width, top);
      this.context.stroke();
    } else if (kind === 'vec') {
      this.drawLine(x, top, x + box.width, top, lineWidth);
      this.context.beginPath();
      this.context.moveTo(x + box.width, top);
      this.context.lineTo(x + box.width - box.fontSize * 0.2, top - box.fontSize * 0.12);
      this.context.moveTo(x + box.width, top);
      this.context.lineTo(x + box.width - box.fontSize * 0.2, top + box.fontSize * 0.12);
      this.context.stroke();
    } else if (kind === 'dot' || kind === 'ddot') {
      const radius = Math.max(box.fontSize * 0.055, 1);
      const center = x + box.width * 0.5;
      this.context.beginPath();
      this.context.arc(center - (kind === 'ddot' ? radius * 1.8 : 0), top, radius, 0, Math.PI * 2);
      if (kind === 'ddot') {
        this.context.moveTo(center + radius * 2.8, top);
        this.context.arc(center + radius * 1.8, top, radius, 0, Math.PI * 2);
      }
      this.context.fill();
    } else if (kind === 'tilde') {
      this.context.beginPath();
      this.context.lineWidth = lineWidth;
      this.context.moveTo(x, top);
      this.context.quadraticCurveTo(x + box.width * 0.25, top - box.fontSize * 0.14, x + box.width * 0.5, top);
      this.context.quadraticCurveTo(x + box.width * 0.75, top + box.fontSize * 0.14, x + box.width, top);
      this.context.stroke();
    } else {
      this.drawLine(x, top, x + box.width, top, lineWidth);
    }
  }

  private drawLine(x1: number, y1: number, x2: number, y2: number, width: number): void {
    this.context.beginPath();
    this.context.lineWidth = width;
    this.context.moveTo(x1, y1);
    this.context.lineTo(x2, y2);
    this.context.stroke();
  }

  private useFont(fontSize: number): void {
    this.context.font = `normal 400 ${fontSize}vp serif`;
  }
}

function matrixFenceWidth(environment: string, fontSize: number): number {
  if (environment === 'matrix' || environment === 'array') {
    return fontSize * 0.12;
  }
  if (environment === 'cases') {
    return fontSize * 0.7;
  }
  return fontSize * 0.55;
}
