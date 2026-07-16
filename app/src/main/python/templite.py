"""
Templite - a light-weight template engine.

Based on Trent Mick's ActiveState Recipe 496702
 (https://code.activestate.com/recipes/496702).
Syntax:
  ${expr}     - evaluate expression, insert result as string
  ${{code}}   - execute code block (use 'emit(text)' to output)

Usage:
  t = Templite("Hello ${name}!")
  result = t.render(name="World")
"""

import re


class Templite:

    def __init__(self, text):
        self._code = self._compile(text)

    def _compile(self, text):
        code_lines = []

        indent = 0
        # Split on ${...} and ${{...}}
        tokens = re.split(r'(\$\{\{.*?\}\}|\$\{.*?\})', text, flags=re.DOTALL)

        for token in tokens:
            if token.startswith('${{') and token.endswith('}}'):
                # Code block
                code = token[3:-2]
                for line in code.splitlines():
                    stripped = line.strip()
                    if not stripped:
                        continue
                    # Handle dedent for pass/end markers
                    if stripped == 'pass':
                        indent -= 1
                        continue
                    code_lines.append('    ' * indent + stripped)
                    # Auto-indent after colon
                    if stripped.endswith(':'):
                        indent += 1
            elif token.startswith('${') and token.endswith('}'):
                # Expression
                expr = token[2:-1].strip()
                code_lines.append('    ' * indent +
                                  '_emit(str(%s))' % expr)
            else:
                # Literal text
                if token:
                    code_lines.append('    ' * indent +
                                      '_emit(%r)' % token)

        return '\n'.join(code_lines)

    def render(self, **namespace):
        ns = dict(namespace)
        ns['_result'] = []
        ns['_emit'] = ns['_result'].append
        ns['emit'] = ns['_result'].append
        exec(self._code, ns)
        return ''.join(ns['_result'])
