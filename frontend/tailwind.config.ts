import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // 深色底 + 橄榄绿强调色，对齐参考截图的基本观感
        ink: {
          950: "#0b0b0c",
          900: "#111113",
          850: "#16161a",
          800: "#1c1c21",
          700: "#26262d",
          600: "#33333c",
        },
        accent: {
          DEFAULT: "#b9d64c",
          soft: "#d7e88f",
          deep: "#6f8a1f",
        },
      },
      fontFamily: {
        sans: [
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Microsoft YaHei",
          "PingFang SC",
          "Hiragino Sans GB",
          "sans-serif",
        ],
      },
      boxShadow: {
        card: "0 1px 0 0 rgba(255,255,255,0.04) inset, 0 12px 32px -12px rgba(0,0,0,0.8)",
      },
      keyframes: {
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
      animation: {
        shimmer: "shimmer 1.6s linear infinite",
      },
    },
  },
  plugins: [],
};

export default config;
