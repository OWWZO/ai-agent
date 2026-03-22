Windows project commands for ui project:
- `pnpm install` or `npm install` to install deps (repo currently uses pnpm-lock.yaml).
- `pnpm dev` or `npm run dev` to start local Vite dev server.
- `npm run build` for full TypeScript + Vite production build.
- `pnpm exec vite build --mode production` for frontend-only bundling checks when isolating TS issues.
- `npm run lint` to run ESLint.
- `npm run fix` to apply ESLint fixes.
Useful Windows commands: `Get-ChildItem`, `Get-Content`, `Select-String`, `rg`, `git status --short`, `git diff`.