<script>
	import { Select } from '@kkutuio/ui';

	/**
	 * The shared dropdown (WAI-ARIA select-only combobox + listbox), under the path the game imports it from.
	 * @type {{options:{value:any,label:string,disabled?:boolean,badge?:string}[], value:any, label?:string, onchange?:(v:any)=>void, leading?:import('svelte').Snippet<[any]>, dense?:boolean}}
	 */
	let { value = $bindable(), ...rest } = $props();
</script>

<Select bind:value {...rest} />
