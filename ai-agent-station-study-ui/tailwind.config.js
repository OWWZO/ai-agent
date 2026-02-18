/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{js,jsx,ts,tsx}', './*.html'],
  important: true,
  theme: {
    extend: {
      colors: {
        primary: '#4040FFB2',
      },
      scrollbar: {
        hidden: {
          '::-webkit-scrollbar': {
            display: 'none',
          },
          'scrollbar-width': 'none',
          '-ms-overflow-style': 'none',
        },
      },
    },
  },
  corePlugins: {
    preflight: false,
  },
};
