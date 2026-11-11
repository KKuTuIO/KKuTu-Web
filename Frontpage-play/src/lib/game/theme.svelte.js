import { themeStyle } from './theme.js';

/** The theme the client wears now (the palette of the worn 테마 item), set by the lobby page and read by the play layout. */
export const worn = $state({ theme: null });

/** The layout's inline style: the theme's tokens over the stylesheet's own (and nothing when no theme is worn). */
export const wornStyle = () => (worn.theme ? themeStyle(worn.theme) : undefined);
