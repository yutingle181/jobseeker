/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts}'],
  theme: {
    extend: {
      colors: {
        primary: '#2563EB',
        primaryDark: '#1D4ED8',
        primaryLight: '#3B82F6',
        bgSoft: '#F5F7FA',
        ink: '#1F2937',
        muted: '#6B7280',
        success: '#16A34A',
        danger: '#DC2626',
        warn: '#F59E0B',
      },
      fontFamily: {
        sans: ['PingFang SC', 'Microsoft YaHei', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 3px rgba(15,23,42,0.06), 0 1px 2px rgba(15,23,42,0.04)',
        cardHover: '0 10px 24px rgba(37,99,235,0.14), 0 4px 10px rgba(15,23,42,0.06)',
      },
      keyframes: {
        float: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' },
        },
      },
      animation: { float: 'float 5s ease-in-out infinite' },
    },
  },
  plugins: [],
}
