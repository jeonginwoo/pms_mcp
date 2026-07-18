# React / TypeScript Conventions (frontend/)

> Scope: the chat widget and the PMS front-end. The existing prototype (`frontend/`) is written in JS — **new files are written in TS**; migrate existing files incrementally as you touch them.

## 1. Components
- **Function components only.** Class components are forbidden.
- One file = one component. File names are PascalCase (`ChatPanel.tsx`).
- Declare props as `interface Props`, and export as `export default function ChatPanel({ ... }: Props)`.
- Consider splitting any component that exceeds 150 lines.

## 2. Types
- `any` is forbidden. When unavoidable, use `unknown` + narrowing.
- Collect API response types in `src/types/` and match their names to the server DTOs (Ubiquitous Language).
- Minimize type assertions (`as`) — prefer type guard functions.

## 3. State & data
- Local state via `useState`/`useReducer`; server state goes through the fetch wrapper (`src/api.ts`) — no direct `fetch` calls inside components.
- Global state follows the existing store pattern (`store.jsx`); introducing a new library follows the `기술_선택_근거.md` process.
- Never omit the `useEffect` dependency array. Do not suppress lint warnings.

## 4. Chat-widget-specific rules (PRD FR-AI series)
- Over 2,000 input characters: disable the send button + show guidance — **block before it reaches the server** (FR-AI-02).
- Write operations must render a confirmation card ([실행]/[취소]) — never create a write path without the card (FR-AI-04).
- Show progress state while waiting for a response + provide an abort button (FR-AI-03).
- Every response gets 👍/👎 feedback buttons; 👎 asks for a reason (FR-AI-05).
- Render tool results and model responses as text — `dangerouslySetInnerHTML` is forbidden (injection defense).

## 5. Style & quality
- Follow the existing `styles.css`/`theme.js` system. Inline styles only for dynamic values.
- Keep ESLint warnings at 0. `eslint-disable` requires a reason comment.
- Tests: logic (parsers, state transitions) via Vitest unit tests. The confirmation-card flow (FR-AI-04) requires a component test.
