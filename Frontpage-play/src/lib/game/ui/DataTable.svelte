<script>
	/**
	 * The one table of the game client: a rounded frame that scrolls (sideways when it is too wide, and up and down when `maxHeight` is
	 * set, the header staying put), a muted header row, and ruled body rows. The profile 통계, the ranking and the report results use it.
	 *
	 * columns   [{ key, label, align?: 'left'|'center'|'right', width?: 'w-20', class?: '', pad?: 'px-2 py-1', nowrap?: boolean,
	 *              numeric?: boolean, hideLabel?: boolean }]   `class` goes on every body cell of the column; `pad` replaces the padding
	 * rows      the data; `rowKey(row, i)` identifies a row (default `row.key ?? row.id ?? i`)
	 * cell      snippet (row, column, index): the content of a cell (default `row[column.key]`)
	 * wide      snippet (row) + `isWide(row)`: a row that is one cell across the whole table (a message instead of columns)
	 * empty     snippet shown under the table when there are no rows
	 * onrowclick(row): the whole row is a button (mouse and Enter/Space); a control inside it keeps its own clicks
	 * bare: no frame and a header that only screen readers see, for a list that sits straight on its page
	 */
	let {
		columns,
		rows,
		rowKey = (row, i) => row.key ?? row.id ?? i,
		caption = '',
		dense = false,
		minWidth = '',
		maxHeight = '',
		busy = false,
		bare = false,
		onrowclick = undefined,
		rowClass = undefined,
		isWide = undefined,
		cell = undefined,
		wide = undefined,
		empty = undefined
	} = $props();

	const ALIGN = { left: 'text-left', center: 'text-center', right: 'text-right' };
	const headPad = $derived(dense ? 'px-1.5 py-1.5' : 'px-3 py-2.5');
	const bodyPad = $derived(dense ? 'px-1.5 py-1.5' : 'px-3 py-2');
	const cellClass = (col) => `${col.pad ?? bodyPad} ${ALIGN[col.align ?? 'left']} ${col.nowrap ? 'whitespace-nowrap' : ''} ${col.numeric ? 'tabular-nums' : ''} ${col.class ?? ''}`;

	function key(e, row) {
		if (e.target !== e.currentTarget || (e.key !== 'Enter' && e.key !== ' ')) return;
		e.preventDefault();
		onrowclick(row);
	}
</script>

<div class="{bare ? '' : 'overflow-auto rounded-xl border-[1.5px] border-(color:--kk-line)'} {maxHeight}" data-table>
	<table class="w-full {minWidth} {dense ? 'text-[13px]' : 'text-sm'}">
		{#if caption}<caption class="sr-only">{caption}</caption>{/if}
		<thead class={bare ? 'sr-only' : `sticky top-0 z-[1] bg-(--kk-panel) text-(color:--kk-muted) ${dense ? 'text-[11px]' : 'text-xs'}`}>
			<tr>
				{#each columns as col (col.key)}
					<th scope="col" class="{col.pad ? col.pad : headPad} {ALIGN[col.align ?? 'left']} {col.width ?? ''} font-bold {col.nowrap ? 'whitespace-nowrap' : ''}">
						{#if col.hideLabel}<span class="sr-only">{col.label}</span>{:else}{col.label}{/if}
					</th>
				{/each}
			</tr>
		</thead>
		<tbody class="{busy ? 'opacity-60' : ''} {bare ? '[&>tr:first-child]:border-t-0' : ''}">
			{#each rows as row, i (rowKey(row, i))}
				<tr
					class="border-t border-(color:--kk-line) {onrowclick ? 'group cursor-pointer hover:bg-(--kk-accent-soft) focus-visible:bg-(--kk-accent-soft)' : ''} {rowClass?.(row) ?? ''}"
					data-row={rowKey(row, i)}
					tabindex={onrowclick ? 0 : undefined}
					onclick={onrowclick ? () => onrowclick(row) : undefined}
					onkeydown={onrowclick ? (e) => key(e, row) : undefined}
				>
					{#if isWide?.(row)}
						<td class="{bodyPad} break-keep" colspan={columns.length}>{@render wide?.(row)}</td>
					{:else}
						{#each columns as col (col.key)}
							<td class={cellClass(col)}>{#if cell}{@render cell(row, col, i)}{:else}{row[col.key]}{/if}</td>
						{/each}
					{/if}
				</tr>
			{/each}
		</tbody>
	</table>
	{@render empty?.()}
</div>
