export abstract class MathNode {
}

export class MathTextNode extends MathNode {
  value: string;

  constructor(value: string) {
    super();
    this.value = value;
  }
}

export class MathRowNode extends MathNode {
  children: MathNode[];

  constructor(children: MathNode[]) {
    super();
    this.children = children;
  }
}

export class MathFracNode extends MathNode {
  numerator: MathNode;
  denominator: MathNode;
  bar: boolean;

  constructor(numerator: MathNode, denominator: MathNode, bar: boolean = true) {
    super();
    this.numerator = numerator;
    this.denominator = denominator;
    this.bar = bar;
  }
}

export class MathSqrtNode extends MathNode {
  index: MathNode | null;
  body: MathNode;

  constructor(index: MathNode | null, body: MathNode) {
    super();
    this.index = index;
    this.body = body;
  }
}

export class MathMatrixNode extends MathNode {
  environment: string;
  rows: MathNode[][];

  constructor(environment: string, rows: MathNode[][]) {
    super();
    this.environment = environment;
    this.rows = rows;
  }
}

export class MathScriptedNode extends MathNode {
  base: MathNode;
  sub: MathNode | null;
  sup: MathNode | null;

  constructor(base: MathNode, sub: MathNode | null, sup: MathNode | null) {
    super();
    this.base = base;
    this.sub = sub;
    this.sup = sup;
  }
}

export class MathOperatorNode extends MathNode {
  symbol: string;
  under: MathNode | null;
  over: MathNode | null;

  constructor(symbol: string, under: MathNode | null, over: MathNode | null) {
    super();
    this.symbol = symbol;
    this.under = under;
    this.over = over;
  }
}

export class MathDelimitedNode extends MathNode {
  left: string;
  body: MathNode;
  right: string;

  constructor(left: string, body: MathNode, right: string) {
    super();
    this.left = left;
    this.body = body;
    this.right = right;
  }
}

export class MathAccentNode extends MathNode {
  kind: string;
  body: MathNode;

  constructor(kind: string, body: MathNode) {
    super();
    this.kind = kind;
    this.body = body;
  }
}

export class MathBigOpNode extends MathNode {
  symbol: string;
  limits: boolean;

  constructor(symbol: string, limits: boolean) {
    super();
    this.symbol = symbol;
    this.limits = limits;
  }
}

enum ParseStop {
  None,
  Brace,
  Bracket,
  Right,
}

export function parseMathNode(latex: string, display: boolean): MathNode {
  if (latex.length === 0) {
    return new MathTextNode('');
  }
  try {
    return new LatexMathParser(latex, display).parse();
  } catch (_error) {
    return new MathTextNode(latex);
  }
}

class LatexMathParser {
  private source: string;
  private display: boolean;
  private index: number = 0;

  constructor(source: string, display: boolean) {
    this.source = source;
    this.display = display;
  }

  parse(): MathNode {
    return this.parseExpression(ParseStop.None);
  }

  private parseExpression(stop: ParseStop): MathNode {
    const nodes: MathNode[] = [];
    let text: string = '';
    while (this.index < this.source.length) {
      this.skipWhitespace();
      if (this.index >= this.source.length || this.shouldStop(stop)) {
        break;
      }
      let node: MathNode;
      const current = this.source.charAt(this.index);
      if (current === '^' || current === '_') {
        node = this.parseScripts(new MathTextNode(''));
      } else {
        const atom = this.parseAtom();
        if (atom === null) {
          break;
        }
        node = this.parseScripts(atom);
      }
      if (node instanceof MathTextNode) {
        text += node.value;
      } else {
        if (text.length > 0) {
          nodes.push(new MathTextNode(text));
          text = '';
        }
        nodes.push(node);
      }
    }
    if (text.length > 0) {
      nodes.push(new MathTextNode(text));
    }
    if (nodes.length === 0) {
      return new MathTextNode('');
    }
    if (nodes.length === 1) {
      return nodes[0];
    }
    return new MathRowNode(nodes);
  }

  private shouldStop(stop: ParseStop): boolean {
    const current = this.source.charAt(this.index);
    if (current === '}' || current === '&') {
      return true;
    }
    if (current === ']' && stop === ParseStop.Bracket) {
      return true;
    }
    return stop === ParseStop.Right && this.startsWith('\\right');
  }

  private parseAtom(): MathNode | null {
    this.skipWhitespace();
    if (this.index >= this.source.length) {
      return null;
    }
    const current = this.source.charAt(this.index);
    if (current === '{') {
      return this.parseGroup();
    }
    if (current === '}' || current === '&' || current === ']') {
      return null;
    }
    if (current === '\\') {
      return this.parseCommand();
    }
    this.index += 1;
    return new MathTextNode(current);
  }

  private parseScripts(base: MathNode): MathNode {
    let sub: MathNode | null = null;
    let sup: MathNode | null = null;
    while (this.index < this.source.length) {
      this.skipWhitespace();
      const current = this.source.charAt(this.index);
      if (current === '_') {
        this.index += 1;
        sub = this.mergeScript(sub, this.parseScriptBody());
      } else if (current === '^') {
        this.index += 1;
        sup = this.mergeScript(sup, this.parseScriptBody());
      } else {
        break;
      }
    }
    if (sub === null && sup === null) {
      return base;
    }
    if (base instanceof MathBigOpNode) {
      if (this.display && base.limits) {
        return new MathOperatorNode(base.symbol, sub, sup);
      }
      return new MathScriptedNode(new MathTextNode(base.symbol), sub, sup);
    }
    return new MathScriptedNode(base, sub, sup);
  }

  private mergeScript(existing: MathNode | null, extra: MathNode): MathNode {
    if (existing === null) {
      return extra;
    }
    return new MathRowNode([existing, extra]);
  }

  private parseScriptBody(): MathNode {
    this.skipWhitespace();
    if (this.index >= this.source.length) {
      return new MathTextNode('');
    }
    if (this.source.charAt(this.index) === '{') {
      return this.parseGroup();
    }
    const atom = this.parseAtom();
    return atom === null ? new MathTextNode('') : atom;
  }

  private parseGroup(): MathNode {
    if (this.source.charAt(this.index) !== '{') {
      return new MathTextNode('');
    }
    this.index += 1;
    const node = this.parseExpression(ParseStop.Brace);
    if (this.index < this.source.length && this.source.charAt(this.index) === '}') {
      this.index += 1;
    }
    return node;
  }

  private parseCommand(): MathNode {
    this.index += 1;
    if (this.index >= this.source.length) {
      return new MathTextNode('\\');
    }
    const first = this.source.charAt(this.index);
    if (!isLetter(first)) {
      this.index += 1;
      switch (first) {
        case '\\':
          return new MathTextNode(this.display ? '\n' : ' ');
        case ',':
        case ':':
        case ';':
        case '!':
          return new MathTextNode(' ');
        case '|':
          return new MathTextNode('∥');
        default:
          return new MathTextNode(first);
      }
    }
    let name = '';
    while (this.index < this.source.length && isLetter(this.source.charAt(this.index))) {
      name += this.source.charAt(this.index);
      this.index += 1;
    }
    if (this.index < this.source.length && this.source.charAt(this.index) === '*') {
      this.index += 1;
    }
    this.skipWhitespace();
    return this.layoutCommand(name);
  }

  private layoutCommand(name: string): MathNode {
    const bigOperator = bigOperatorForName(name);
    if (bigOperator !== null) {
      return bigOperator;
    }
    const symbol = symbolForName(name);
    if (symbol !== null) {
      return new MathTextNode(symbol);
    }
    switch (name) {
      case 'frac':
      case 'dfrac':
      case 'tfrac':
      case 'cfrac':
        return new MathFracNode(this.parseGroupOrAtom(), this.parseGroupOrAtom(), true);
      case 'binom':
      case 'dbinom':
      case 'tbinom':
        return new MathDelimitedNode('(', new MathFracNode(this.parseGroupOrAtom(), this.parseGroupOrAtom(), false), ')');
      case 'sqrt': {
        let rootIndex: MathNode | null = null;
        if (this.index < this.source.length && this.source.charAt(this.index) === '[') {
          this.index += 1;
          rootIndex = this.parseExpression(ParseStop.Bracket);
          if (this.index < this.source.length && this.source.charAt(this.index) === ']') {
            this.index += 1;
          }
        }
        return new MathSqrtNode(rootIndex, this.parseGroupOrAtom());
      }
      case 'overline':
      case 'underline':
      case 'hat':
      case 'bar':
      case 'vec':
      case 'tilde':
      case 'dot':
      case 'ddot':
        return new MathAccentNode(name, this.parseGroupOrAtom());
      case 'begin':
        return this.parseEnvironment();
      case 'end':
        this.parseGroup();
        return new MathTextNode('');
      case 'left':
        return this.parseLeftRight();
      case 'right':
        return new MathTextNode(this.parseFence());
      case 'text':
      case 'textrm':
      case 'mbox':
        return new MathTextNode(this.parseRawGroup());
      case 'mathrm':
      case 'operatorname':
      case 'mathbf':
      case 'mathit':
      case 'mathsf':
      case 'mathtt':
      case 'textbf':
        return this.parseGroupOrAtom();
      case 'quad':
        return new MathTextNode('  ');
      case 'qquad':
        return new MathTextNode('    ');
      default:
        if (isNamedFunction(name)) {
          return new MathTextNode(name);
        }
        return new MathTextNode(`\\${name}`);
    }
  }

  private parseLeftRight(): MathNode {
    const left = this.parseFence();
    const body = this.parseExpression(ParseStop.Right);
    this.skipWhitespace();
    let right = '';
    if (this.startsWith('\\right')) {
      this.index += 6;
      right = this.parseFence();
    }
    return new MathDelimitedNode(left, body, right);
  }

  private parseFence(): string {
    this.skipWhitespace();
    if (this.index >= this.source.length) {
      return '';
    }
    const current = this.source.charAt(this.index);
    if (current === '.') {
      this.index += 1;
      return '';
    }
    if (current !== '\\') {
      this.index += 1;
      return current;
    }
    this.index += 1;
    if (this.index >= this.source.length) {
      return '\\';
    }
    const first = this.source.charAt(this.index);
    if (!isLetter(first)) {
      this.index += 1;
      return first === '|' ? '∥' : first;
    }
    let name = '';
    while (this.index < this.source.length && isLetter(this.source.charAt(this.index))) {
      name += this.source.charAt(this.index);
      this.index += 1;
    }
    return fenceForName(name);
  }

  private parseEnvironment(): MathNode {
    const environment = flattenMathNode(this.parseGroup()).trim();
    let body = '';
    while (this.index < this.source.length) {
      if (this.startsWith('\\end')) {
        this.index += 4;
        const endName = flattenMathNode(this.parseGroup()).trim();
        if (endName === environment || endName.length === 0) {
          break;
        }
        body += `\\end{${endName}}`;
      } else {
        body += this.source.charAt(this.index);
        this.index += 1;
      }
    }
    if (environment === 'array') {
      body = stripLeadingGroup(body);
    }
    const rows: MathNode[][] = [];
    const rawRows = body.split('\\\\');
    for (let rowIndex = 0; rowIndex < rawRows.length; rowIndex += 1) {
      const rawRow = rawRows[rowIndex].trim();
      if (rawRow.length === 0) {
        continue;
      }
      const cells: MathNode[] = [];
      const rawCells = rawRow.split('&');
      for (let column = 0; column < rawCells.length; column += 1) {
        cells.push(new LatexMathParser(rawCells[column].trim(), false).parse());
      }
      rows.push(cells);
    }
    if (rows.length === 0) {
      return new MathTextNode('');
    }
    return new MathMatrixNode(environment, rows);
  }

  private parseGroupOrAtom(): MathNode {
    this.skipWhitespace();
    if (this.index >= this.source.length) {
      return new MathTextNode('');
    }
    if (this.source.charAt(this.index) === '{') {
      return this.parseGroup();
    }
    const atom = this.parseAtom();
    return atom === null ? new MathTextNode('') : atom;
  }

  private parseRawGroup(): string {
    this.skipWhitespace();
    if (this.index >= this.source.length || this.source.charAt(this.index) !== '{') {
      const atom = this.parseAtom();
      return atom === null ? '' : flattenMathNode(atom);
    }
    this.index += 1;
    let depth = 1;
    let result = '';
    while (this.index < this.source.length && depth > 0) {
      const current = this.source.charAt(this.index);
      this.index += 1;
      if (current === '{') {
        depth += 1;
        result += current;
      } else if (current === '}') {
        depth -= 1;
        if (depth > 0) {
          result += current;
        }
      } else {
        result += current;
      }
    }
    return result;
  }

  private skipWhitespace(): void {
    while (this.index < this.source.length && isWhitespace(this.source.charAt(this.index))) {
      this.index += 1;
    }
  }

  private startsWith(value: string): boolean {
    return this.source.substring(this.index, this.index + value.length) === value;
  }
}

function flattenMathNode(node: MathNode): string {
  if (node instanceof MathTextNode) {
    return node.value;
  }
  if (node instanceof MathRowNode) {
    let result = '';
    for (let index = 0; index < node.children.length; index += 1) {
      result += flattenMathNode(node.children[index]);
    }
    return result;
  }
  return '';
}

function stripLeadingGroup(raw: string): string {
  const source = raw.trimStart();
  if (!source.startsWith('{')) {
    return source;
  }
  let depth = 0;
  for (let index = 0; index < source.length; index += 1) {
    const current = source.charAt(index);
    if (current === '{') {
      depth += 1;
    } else if (current === '}') {
      depth -= 1;
      if (depth === 0) {
        return source.substring(index + 1).trimStart();
      }
    }
  }
  return source;
}

function isWhitespace(value: string): boolean {
  return value === ' ' || value === '\n' || value === '\r' || value === '\t';
}

function isLetter(value: string): boolean {
  if (value.length === 0) {
    return false;
  }
  const code = value.charCodeAt(0);
  return (code >= 65 && code <= 90) || (code >= 97 && code <= 122);
}

function bigOperatorForName(name: string): MathBigOpNode | null {
  switch (name) {
    case 'sum': return new MathBigOpNode('∑', true);
    case 'prod': return new MathBigOpNode('∏', true);
    case 'coprod': return new MathBigOpNode('∐', true);
    case 'int': return new MathBigOpNode('∫', false);
    case 'iint': return new MathBigOpNode('∬', false);
    case 'iiint': return new MathBigOpNode('∭', false);
    case 'oint': return new MathBigOpNode('∮', false);
    case 'bigcup': return new MathBigOpNode('⋃', true);
    case 'bigcap': return new MathBigOpNode('⋂', true);
    default: return null;
  }
}

function symbolForName(name: string): string | null {
  switch (name) {
    case 'alpha': return 'α'; case 'beta': return 'β'; case 'gamma': return 'γ';
    case 'delta': return 'δ'; case 'epsilon': return 'ε'; case 'varepsilon': return 'ϵ';
    case 'zeta': return 'ζ'; case 'eta': return 'η'; case 'theta': return 'θ';
    case 'vartheta': return 'ϑ'; case 'iota': return 'ι'; case 'kappa': return 'κ';
    case 'lambda': return 'λ'; case 'mu': return 'μ'; case 'nu': return 'ν';
    case 'xi': return 'ξ'; case 'pi': return 'π'; case 'varpi': return 'ϖ';
    case 'rho': return 'ρ'; case 'sigma': return 'σ'; case 'tau': return 'τ';
    case 'upsilon': return 'υ'; case 'phi': return 'φ'; case 'varphi': return 'ϕ';
    case 'chi': return 'χ'; case 'psi': return 'ψ'; case 'omega': return 'ω';
    case 'Gamma': return 'Γ'; case 'Delta': return 'Δ'; case 'Theta': return 'Θ';
    case 'Lambda': return 'Λ'; case 'Xi': return 'Ξ'; case 'Pi': return 'Π';
    case 'Sigma': return 'Σ'; case 'Upsilon': return 'Υ'; case 'Phi': return 'Φ';
    case 'Psi': return 'Ψ'; case 'Omega': return 'Ω';
    case 'infty': return '∞'; case 'partial': return '∂'; case 'nabla': return '∇';
    case 'times': return '×'; case 'cdot': return '·'; case 'div': return '÷';
    case 'pm': return '±'; case 'mp': return '∓'; case 'le': case 'leq': return '≤';
    case 'ge': case 'geq': return '≥'; case 'ne': case 'neq': return '≠';
    case 'approx': return '≈'; case 'equiv': return '≡'; case 'sim': return '∼';
    case 'to': case 'rightarrow': return '→'; case 'leftarrow': return '←';
    case 'leftrightarrow': return '↔'; case 'Rightarrow': return '⇒';
    case 'Leftarrow': return '⇐'; case 'Leftrightarrow': return '⇔';
    case 'in': return '∈'; case 'notin': return '∉'; case 'subset': return '⊂';
    case 'supset': return '⊃'; case 'subseteq': return '⊆'; case 'supseteq': return '⊇';
    case 'cup': return '∪'; case 'cap': return '∩'; case 'emptyset': return '∅';
    case 'forall': return '∀'; case 'exists': return '∃'; case 'neg': return '¬';
    case 'land': return '∧'; case 'lor': return '∨'; case 'ldots': return '…';
    case 'cdots': return '⋯'; case 'vdots': return '⋮'; case 'ddots': return '⋱';
    default: return null;
  }
}

function fenceForName(name: string): string {
  switch (name) {
    case 'langle': return '⟨'; case 'rangle': return '⟩';
    case 'lceil': return '⌈'; case 'rceil': return '⌉';
    case 'lfloor': return '⌊'; case 'rfloor': return '⌋';
    case 'lVert': case 'rVert': case 'Vert': return '∥';
    case 'vert': return '|'; case 'lbrace': return '{'; case 'rbrace': return '}';
    default: {
      const symbol = symbolForName(name);
      return symbol === null ? name : symbol;
    }
  }
}

function isNamedFunction(name: string): boolean {
  switch (name) {
    case 'sin': case 'cos': case 'tan': case 'cot': case 'sec': case 'csc':
    case 'arcsin': case 'arccos': case 'arctan': case 'sinh': case 'cosh': case 'tanh':
    case 'log': case 'ln': case 'lg': case 'exp': case 'det': case 'dim': case 'ker':
    case 'deg': case 'gcd': case 'hom': case 'arg':
      return true;
    default:
      return false;
  }
}
