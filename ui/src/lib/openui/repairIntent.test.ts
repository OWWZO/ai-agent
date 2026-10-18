import { describe, expect, it } from 'vitest';

import {
  applyIntents,
  MAX_BLOCKS,
  MAX_SEARCH_LENGTH,
  type ReplaceBlock,
} from './repairIntent';

/** 计算器里最长的一条语句，作为"一条语句规模"的参照。 */
const LONG_STATEMENT =
  'equals = Button("=", Action([@Set($display, $op == "" ? $display : $op == "+" ? $stored + $display : $stored - $display)]))';

describe('applyIntents', () => {
  const source = '$display = 0\nt = Text($display + "", "3xl")\nroot = Col([t])';

  it('replaces a unique snippet', () => {
    const { result, appliedCount, failures } = applyIntents(source, [
      {
        searchText: '"3xl"',
        replaceText: '"lg"'
      },
    ]);
    expect(appliedCount).toBe(1);
    expect(failures).toHaveLength(0);
    expect(result).toBe('$display = 0\nt = Text($display + "", "lg")\nroot = Col([t])');
  });

  it('applies multiple non-overlapping blocks from back to front', () => {
    const program = '$a = 1\n$b = 2\n$c = 3\nroot = Col([x, y, z])';
    const { result, appliedCount } = applyIntents(program, [
      {
        searchText: '$a = 1',
        replaceText: '$a = 10'
      },
      {
        searchText: '$c = 3',
        replaceText: '$c = 30'
      },
      {
        searchText: '$b = 2',
        replaceText: '$b = 20'
      },
    ]);
    expect(appliedCount).toBe(3);
    expect(result).toBe('$a = 10\n$b = 20\n$c = 30\nroot = Col([x, y, z])');
  });

  it('supports insertion via an anchor SEARCH', () => {
    const { result } = applyIntents(source, [
      {
        searchText: 'root = Col([t])',
        replaceText: 'root = Col([t, b])\nb = Button("C", @Reset($display))'
      },
    ]);
    expect(result).toContain('b = Button');
    expect(result?.indexOf('root = Col([t, b])')).toBeGreaterThan(0);
  });

  it('supports deletion via an empty REPLACE', () => {
    const program = '$op = ""\nstatus = Caption($op)\nroot = Col([status])';
    const { result, appliedCount } = applyIntents(program, [
      {
        searchText: 'status = Caption($op)\n',
        replaceText: ''
      },
    ]);
    expect(appliedCount).toBe(1);
    expect(result).toBe('$op = ""\nroot = Col([status])');
  });

  it('rejects a SEARCH that does not exist', () => {
    const { result, appliedCount, failures } = applyIntents(source, [
      {
        searchText: 't = Text($display + "","3xl")',
        replaceText: 'x'
      },
    ]);
    expect(appliedCount).toBe(0);
    expect(result).toBeNull();
    expect(failures).toEqual([{
      index: 0,
      reason: 'not_found'
    }]);
  });

  it('rejects a SEARCH that appears more than once', () => {
    const program = 'a = Title("x", "lg")\nb = Caption("x")\nroot = Col([a, b])';
    const { failures } = applyIntents(program, [
      {
        searchText: '"x"',
        replaceText: '"y"'
      },
    ]);
    expect(failures).toEqual([{
      index: 0,
      reason: 'not_unique'
    }]);
  });

  it('rejects an empty SEARCH', () => {
    const { failures } = applyIntents(source, [{
      searchText: '   ',
      replaceText: 'x'
    }]);
    expect(failures).toEqual([{
      index: 0,
      reason: 'empty_search'
    }]);
  });

  it('rejects a SEARCH longer than one statement', () => {
    // 拼出一段超过 SEARCH 上限的程序（整段重写形态）。
    const filler = Array.from({ length: 20 }, (_, i) => `note${i} = Text("备注 ${i}", "xs")`).join('\n');
    const wholeProgram = [source, filler, LONG_STATEMENT].join('\n');
    expect(wholeProgram.length).toBeGreaterThan(MAX_SEARCH_LENGTH);
    const { failures, appliedCount } = applyIntents(wholeProgram, [
      {
        searchText: wholeProgram,
        replaceText: 'root = Col([])'
      },
    ]);
    expect(appliedCount).toBe(0);
    expect(failures).toEqual([{
      index: 0,
      reason: 'too_long'
    }]);
    expect(MAX_SEARCH_LENGTH).toBeGreaterThan(LONG_STATEMENT.length);
  });

  it('keeps the earlier block and drops the overlapping one', () => {
    const program = '$a = 1\n$b = 2\n$c = 3\nroot = Col([x, y, z])';
    const { result, failures } = applyIntents(program, [
      {
        searchText: '$a = 1\n$b = 2',
        replaceText: '$a = 10\n$b = 2'
      },
      {
        searchText: '$b = 2\n$c = 3',
        replaceText: '$b = 20\n$c = 3'
      },
    ]);
    expect(result).toBe('$a = 10\n$b = 2\n$c = 3\nroot = Col([x, y, z])');
    expect(failures).toEqual([{
      index: 1,
      reason: 'overlapping'
    }]);
  });

  it('caps the number of applied blocks', () => {
    const program = Array.from({ length: 6 }, (_, i) => `v${i} = ${i}`).join('\n');
    const blocks: ReplaceBlock[] = Array.from({ length: 5 }, (_, i) => ({
      searchText: `v${i} = ${i}`,
      replaceText: `v${i} = ${i * 10}`,
    }));
    const { appliedCount, failures } = applyIntents(program, blocks);
    expect(appliedCount).toBe(MAX_BLOCKS);
    expect(failures).toHaveLength(5 - MAX_BLOCKS);
  });

  it('drops a duplicate SEARCH at the same position as overlapping', () => {
    // 两个块指向同一位置：唯一性各自通过，但重叠守卫只留先出现的那个。
    const { result, appliedCount, failures } = applyIntents(source, [
      {
        searchText: '"3xl"',
        replaceText: '"lg"'
      },
      {
        searchText: '"3xl"',
        replaceText: '"xl"'
      },
    ]);
    expect(appliedCount).toBe(1);
    expect(result).toContain('"lg"');
    expect(failures).toEqual([{
      index: 1,
      reason: 'overlapping'
    }]);
  });

  it('keeps the source untouched when nothing applies', () => {
    const { result, appliedCount } = applyIntents(source, [
      {
        searchText: 'nope',
        replaceText: 'x'
      },
      {
        searchText: '   ',
        replaceText: 'y'
      },
    ]);
    expect(appliedCount).toBe(0);
    expect(result).toBeNull();
    expect(source).toContain('"3xl"');
  });

  it('keeps a multi-line SEARCH working', () => {
    const { result, appliedCount } = applyIntents(source, [
      {
        searchText: 't = Text($display + "", "3xl")\nroot = Col([t])',
        replaceText: 't = Text($display + "", "lg")\nroot = Col([t])'
      },
    ]);
    expect(appliedCount).toBe(1);
    expect(result).toContain('"lg"');
  });
});
